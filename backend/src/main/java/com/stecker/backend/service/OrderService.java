package com.stecker.backend.service;

import com.stecker.backend.dto.CreateOrderRequest;
import com.stecker.backend.dto.CreateOrderRequest.OrderLimits;
import com.stecker.backend.dto.OrderResponse;
import com.stecker.backend.dto.StaffOrderDtos.ConfirmOrderRequest;
import com.stecker.backend.dto.StaffOrderDtos.OrderDetail;
import com.stecker.backend.dto.StaffOrderDtos.OrderSummary;
import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.model.MenuItem;
import com.stecker.backend.model.OrderItem;
import com.stecker.backend.model.OrderStatus;
import com.stecker.backend.model.StaffAccount;
import com.stecker.backend.repository.CustomerOrderRepository;
import com.stecker.backend.repository.MenuRepository;
import com.stecker.backend.repository.StaffAccountRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orders from the guest's first click to the waiter's confirmation.
 *
 * Prices always come from the database. Once an order is confirmed or
 * cancelled it can no longer change.
 */
@Service
public class OrderService {

    /** The café's time zone: order times are stored as local time here. */
    public static final ZoneId CAFE_ZONE = ZoneId.of("Europe/Berlin");

    /** No 0/O, 1/I, 2/Z, 5/S, 6/G, 8/B: easy to read out and to type. */
    private static final String CODE_ALPHABET = "ACDEFHJKLMNPRTUVWXY3479";
    private static final int CODE_LENGTH = 4;

    private final CustomerOrderRepository orderRepository;
    private final MenuRepository menuRepository;
    private final StaffAccountRepository staffRepository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OrderService(
            CustomerOrderRepository orderRepository,
            MenuRepository menuRepository,
            StaffAccountRepository staffRepository,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.menuRepository = menuRepository;
        this.staffRepository = staffRepository;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- guests

    /**
     * Saves a new order with status PENDING. The same product listed twice
     * becomes one line with the quantities added up.
     */
    @Transactional
    public CustomerOrder createOrder(CreateOrderRequest request) {
        Map<Long, Integer> quantities = collectQuantities(request);

        CustomerOrder order = new CustomerOrder(
                newShortCode(),
                cleanNote(request.note()),
                LocalDateTime.now(clock.withZone(CAFE_ZONE)));

        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            MenuItem menuItem = findMenuItem(entry.getKey());
            requireAvailable(menuItem);
            order.addItem(new OrderItem(order, menuItem, entry.getValue()));
        }

        order.recalculateTotal();
        return orderRepository.save(order);
    }

    /** The guest checks the status. Knowing the reference is the permission. */
    @Transactional(readOnly = true)
    public OrderResponse getForGuest(UUID reference) {
        return OrderResponse.from(find(reference));
    }

    // ----------------------------------------------------------------- staff

    @Transactional(readOnly = true)
    public List<OrderSummary> openOrders() {
        return orderRepository.findAllByStatusOrderByCreatedAtAsc(OrderStatus.PENDING)
                .stream()
                .map(OrderSummary::from)
                .toList();
    }

    /** Confirmed and cancelled since midnight in Bremen, newest first. */
    @Transactional(readOnly = true)
    public List<OrderSummary> handledToday() {
        var midnight = LocalDate.now(clock.withZone(CAFE_ZONE))
                .atStartOfDay(CAFE_ZONE)
                .toInstant();

        return orderRepository
                .findAllByStatusInAndHandledAtGreaterThanEqualOrderByHandledAtDesc(
                        List.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED), midnight)
                .stream()
                .map(OrderSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetail getForStaff(UUID reference) {
        return OrderDetail.from(find(reference));
    }

    /** An open order by the code the guest reads out. */
    @Transactional(readOnly = true)
    public OrderDetail findOpenByCode(String code) {
        String normalized = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);

        return orderRepository
                .findFirstByShortCodeAndStatusOrderByCreatedAtDesc(normalized, OrderStatus.PENDING)
                .map(OrderDetail::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Es gibt keine offene Bestellung mit dem Code „" + normalized + "“."));
    }

    /**
     * The waiter has entered the order into the till system. With
     * {@code items}, that list is the final order (see
     * {@link ConfirmOrderRequest}).
     */
    @Transactional
    public OrderDetail confirm(UUID reference, ConfirmOrderRequest request, long staffId) {
        CustomerOrder order = findOpen(reference);

        if (request != null && request.items() != null) {
            applyFinalLines(order, request.items());
        }

        if (order.getItems().stream().noneMatch(item -> item.getQuantity() > 0)) {
            throw badRequest("Eine Bestellung ohne Artikel kann nicht bestätigt werden. "
                    + "Storniere sie stattdessen.");
        }

        order.confirm(staff(staffId), clock.instant());
        return OrderDetail.from(orderRepository.saveAndFlush(order));
    }

    @Transactional
    public OrderDetail cancel(UUID reference, long staffId) {
        CustomerOrder order = findOpen(reference);
        order.cancel(staff(staffId), clock.instant());
        return OrderDetail.from(orderRepository.saveAndFlush(order));
    }

    private void applyFinalLines(
            CustomerOrder order,
            List<ConfirmOrderRequest.FinalLine> finalLines
    ) {
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (ConfirmOrderRequest.FinalLine line : finalLines) {
            int quantity = wanted.merge(line.menuItemId(), line.quantity(), Integer::sum);
            if (quantity > OrderLimits.MAX_QUANTITY) {
                throw badRequest("Die Anzahl darf höchstens " + OrderLimits.MAX_QUANTITY + " sein.");
            }
        }

        Map<Long, OrderItem> existing = order.getItems().stream()
                .collect(Collectors.toMap(
                        item -> item.getMenuItem().getId(),
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new));

        // Lines already in the order keep their price; missing ones become 0.
        for (OrderItem item : existing.values()) {
            item.setQuantity(wanted.getOrDefault(item.getMenuItem().getId(), 0));
        }

        // New products are added at today's price, if they can be ordered.
        for (Map.Entry<Long, Integer> entry : wanted.entrySet()) {
            if (existing.containsKey(entry.getKey()) || entry.getValue() == 0) {
                continue;
            }
            MenuItem menuItem = findMenuItem(entry.getKey());
            requireAvailable(menuItem);
            order.addItem(OrderItem.addedByStaff(order, menuItem, entry.getValue()));
        }

        order.recalculateTotal();
    }

    // --------------------------------------------------------------- helpers

    private CustomerOrder find(UUID reference) {
        return orderRepository.findByOrderReference(reference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Diese Bestellung gibt es nicht."));
    }

    private CustomerOrder findOpen(UUID reference) {
        CustomerOrder order = find(reference);

        if (!order.isOpen()) {
            String state = order.getStatus() == OrderStatus.CANCELLED ? "storniert" : "bestätigt";
            String who = Optional.ofNullable(order.getHandledBy())
                    .map(staff -> " von " + staff.getDisplayName())
                    .orElse("");
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Diese Bestellung wurde bereits" + who + " " + state + ".");
        }
        return order;
    }

    private StaffAccount staff(long staffId) {
        return staffRepository.getReferenceById(staffId);
    }

    private MenuItem findMenuItem(long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ein Artikel der Bestellung steht nicht auf der "
                                + "Speisekarte (Nummer " + id + ")."));
    }

    private static void requireAvailable(MenuItem menuItem) {
        if (!menuItem.isAvailable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    menuItem.getName() + " ist derzeit nicht verfügbar.");
        }
    }

    /** A code that no other open order uses right now. */
    private String newShortCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            if (!orderRepository.existsByShortCodeAndStatus(code.toString(), OrderStatus.PENDING)) {
                return code.toString();
            }
        }
        // 23^4 = 279,841 codes; reaching this needs thousands of open orders.
        throw new IllegalStateException("No free short code");
    }

    private static String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.strip();
    }

    /** Checks the request and adds up quantities per product, in order. */
    private static Map<Long, Integer> collectQuantities(CreateOrderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw badRequest("Die Bestellung enthält keine Artikel.");
        }

        Map<Long, Integer> quantities = new LinkedHashMap<>();

        for (CreateOrderRequest.OrderItemRequest item : request.items()) {
            if (item == null || item.menuItemId() == null || item.quantity() <= 0) {
                throw badRequest("Ein Artikel oder eine Anzahl der Bestellung ist ungültig.");
            }

            int quantity = quantities.merge(item.menuItemId(), item.quantity(), Integer::sum);

            if (quantity > OrderLimits.MAX_QUANTITY) {
                throw badRequest("Die Anzahl darf höchstens "
                        + OrderLimits.MAX_QUANTITY + " sein.");
            }
        }

        if (quantities.size() > OrderLimits.MAX_LINES) {
            throw badRequest("Eine Bestellung kann höchstens "
                    + OrderLimits.MAX_LINES + " verschiedene Artikel enthalten.");
        }

        return quantities;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
