package com.stecker.backend.service;

import com.stecker.backend.dto.CreateOrderRequest;
import com.stecker.backend.dto.OrderResponse;
import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.model.MenuItem;
import com.stecker.backend.model.OrderItem;
import com.stecker.backend.model.OrderStatus;
import com.stecker.backend.repository.CustomerOrderRepository;
import com.stecker.backend.repository.MenuRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final CustomerOrderRepository orderRepository;
    private final MenuRepository menuRepository;
    private final Clock clock;

    @Autowired
    public OrderService(
            CustomerOrderRepository orderRepository,
            MenuRepository menuRepository
    ) {
        this(orderRepository, menuRepository, Clock.systemUTC());
    }

    /** Lets tests supply a fixed clock for confirmation times. */
    OrderService(
            CustomerOrderRepository orderRepository,
            MenuRepository menuRepository,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.menuRepository = menuRepository;
        this.clock = clock;
    }

    /** Guest places a new order. It starts as PENDING. */
    @Transactional
    public CustomerOrder createOrder(CreateOrderRequest request) {
        CustomerOrder order = new CustomerOrder(BigDecimal.ZERO);
        List<OrderItem> items = buildItems(order, request);

        order.replaceItems(items, sumLineTotals(items));

        return orderRepository.save(order);
    }

    /** Waiter opens an order from its QR code / reference. */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderReference) {
        return OrderResponse.from(findOrder(orderReference));
    }

    /**
     * Waiter replaces the order's lines with the final products and
     * quantities agreed with the guest. Prices come from the menu again.
     * Only allowed while the order is still PENDING.
     */
    @Transactional
    public OrderResponse updateItems(
            UUID orderReference,
            CreateOrderRequest request
    ) {
        CustomerOrder order = findOrder(orderReference);
        requirePending(order, "changed");

        List<OrderItem> items = buildItems(order, request);
        order.replaceItems(items, sumLineTotals(items));

        return OrderResponse.from(order);
    }

    /**
     * Waiter confirms the order was entered into the official till.
     * Records the confirmation time used by the analytics.
     */
    @Transactional
    public OrderResponse confirmOrder(UUID orderReference) {
        CustomerOrder order = findOrder(orderReference);
        requirePending(order, "confirmed");

        order.confirm(Instant.now(clock));

        return OrderResponse.from(order);
    }

    /** Waiter cancels an order that will not be served. */
    @Transactional
    public OrderResponse cancelOrder(UUID orderReference) {
        CustomerOrder order = findOrder(orderReference);
        requirePending(order, "cancelled");

        order.cancel();

        return OrderResponse.from(order);
    }

    private CustomerOrder findOrder(UUID orderReference) {
        return orderRepository.findByOrderReference(orderReference)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found: " + orderReference
                ));
    }

    private static void requirePending(CustomerOrder order, String action) {
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending orders can be " + action
                            + ". This order is " + order.getStatus() + "."
            );
        }
    }

    /**
     * Turns requested product IDs and quantities into order lines,
     * using the current menu prices. Rejects invalid requests.
     */
    private List<OrderItem> buildItems(
            CustomerOrder order,
            CreateOrderRequest request
    ) {
        if (request == null
                || request.items() == null
                || request.items().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "An order must contain at least one item."
            );
        }

        List<OrderItem> orderItems = new ArrayList<>();

        for (CreateOrderRequest.OrderItemRequest requestedItem
                : request.items()) {

            if (requestedItem == null
                    || requestedItem.menuItemId() == null
                    || requestedItem.quantity() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid product or quantity."
                );
            }

            MenuItem menuItem = menuRepository
                    .findById(requestedItem.menuItemId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Menu item not found: "
                                    + requestedItem.menuItemId()
                    ));

            if (!menuItem.isAvailable()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Product is currently unavailable: "
                                + menuItem.getName()
                );
            }

            orderItems.add(new OrderItem(
                    order,
                    menuItem,
                    requestedItem.quantity()
            ));
        }

        return orderItems;
    }

    private static BigDecimal sumLineTotals(List<OrderItem> items) {
        return items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
