package com.stecker.backend.synthetic;

import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.model.DataSource;
import com.stecker.backend.model.MenuItem;
import com.stecker.backend.model.OrderItem;
import com.stecker.backend.repository.CustomerOrderRepository;
import com.stecker.backend.repository.LocalEventRepository;
import com.stecker.backend.repository.MenuRepository;
import com.stecker.backend.repository.WeatherObservationRepository;
import com.stecker.backend.synthetic.SyntheticOrderGenerator.GeneratedOrder;
import com.stecker.backend.synthetic.SyntheticOrderGenerator.Line;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Saves a generated order history, and continues it day by day
 * ({@link #continueHistory}). Only ever deletes or writes orders with
 * dataSource = SYNTHETIC; REAL orders are never touched.
 */
@Service
public class SyntheticOrderService {

    private static final int FLUSH_EVERY = 500;

    private final CustomerOrderRepository orderRepository;
    private final MenuRepository menuRepository;
    private final WeatherObservationRepository weatherRepository;
    private final LocalEventRepository eventRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    public SyntheticOrderService(
            CustomerOrderRepository orderRepository,
            MenuRepository menuRepository,
            WeatherObservationRepository weatherRepository,
            LocalEventRepository eventRepository,
            EntityManager entityManager
    ) {
        this.orderRepository = orderRepository;
        this.menuRepository = menuRepository;
        this.weatherRepository = weatherRepository;
        this.eventRepository = eventRepository;
        this.entityManager = entityManager;
        this.clock = Clock.systemUTC();
    }

    public record Summary(
            LocalDate start,
            LocalDate end,
            long seed,
            long deletedOrders,
            int orders,
            int confirmed,
            int cancelled,
            int lines,
            BigDecimal confirmedRevenue
    ) {
    }

    /** What continueHistory added: orders for the days from–to. */
    public record Continuation(LocalDate from, LocalDate to, int orders) {
    }

    private record Saved(int confirmed, int lines, BigDecimal confirmedRevenue) {
    }

    @Transactional
    public Summary generate(LocalDate start, LocalDate end, long seed, boolean replace) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date is before start date.");
        }
        if (!end.isBefore(LocalDate.now(clock.withZone(SyntheticScenario.ZONE)))) {
            throw new IllegalArgumentException(
                    "The history must end before today; " + end + " is not in the past.");
        }

        List<MenuItem> menu = availableMenu();

        long existing = orderRepository.countByDataSource(DataSource.SYNTHETIC);
        if (existing > 0 && !replace) {
            throw new IllegalStateException(existing + " synthetic orders already exist. "
                    + "Run again with --stecker.synthetic.replace=true to replace them.");
        }

        long deleted = 0;
        if (existing > 0) {
            orderRepository.deleteItemsByDataSource(DataSource.SYNTHETIC);
            deleted = orderRepository.deleteOrdersByDataSource(DataSource.SYNTHETIC);
        }

        List<GeneratedOrder> orders = generator(menu, seed).generate(start, end);

        validate(orders, start, end);
        Saved saved = save(orders);

        return new Summary(start, end, seed, deleted, orders.size(), saved.confirmed(),
                orders.size() - saved.confirmed(), saved.lines(), saved.confirmedRevenue());
    }

    /**
     * Adds orders for the days since the latest synthetic order, up to the
     * last day that is over and has measured weather for every opening hour
     * (normally yesterday). For running the site locally without real
     * guests: the dashboard and the forecast keep getting fresh history.
     *
     * The new days are what one long generator run with the same seed would
     * have produced: the history is generated again from its first day and
     * only the new days are kept. Running it again the same day adds nothing.
     *
     * @return what was added; empty when there is no synthetic history to
     *         continue or nothing new to add
     */
    @Transactional
    public Optional<Continuation> continueHistory(long seed) {
        return continueHistory(seed, LocalDate.now(clock.withZone(SyntheticScenario.ZONE)));
    }

    @Transactional
    Optional<Continuation> continueHistory(long seed, LocalDate today) {
        Optional<LocalDateTime> first = orderRepository.findFirstCreatedAt(DataSource.SYNTHETIC);
        Optional<LocalDateTime> last = orderRepository.findLastCreatedAt(DataSource.SYNTHETIC);
        Optional<Instant> latestWeather = weatherRepository.findTopByOrderByObservedAtDesc()
                .map(hour -> hour.getObservedAt());
        if (first.isEmpty() || last.isEmpty() || latestWeather.isEmpty()) {
            return Optional.empty();
        }

        LocalDate from = last.get().toLocalDate().plusDays(1);
        LocalDate to = lastCompleteDay(today, latestWeather.get());
        if (to.isBefore(from)) {
            return Optional.empty();
        }

        List<GeneratedOrder> orders = generator(availableMenu(), seed)
                .generate(first.get().toLocalDate(), to).stream()
                .filter(order -> !order.createdAt().atZone(SyntheticScenario.ZONE)
                        .toLocalDate().isBefore(from))
                .toList();

        validate(orders, from, to);
        save(orders);
        return Optional.of(new Continuation(from, to, orders.size()));
    }

    /**
     * The last day that is over and has weather up to its last opening hour:
     * yesterday, or earlier when the weather has not been fetched that far.
     */
    static LocalDate lastCompleteDay(LocalDate today, Instant latestWeather) {
        LocalDateTime latest = LocalDateTime.ofInstant(latestWeather, SyntheticScenario.ZONE);
        LocalTime lastOpeningHour = CafeCalendar.lastOrderBefore()
                .minusNanos(1).truncatedTo(ChronoUnit.HOURS);
        LocalDate covered = latest.toLocalTime().isBefore(lastOpeningHour)
                ? latest.toLocalDate().minusDays(1)
                : latest.toLocalDate();
        LocalDate yesterday = today.minusDays(1);
        return covered.isBefore(yesterday) ? covered : yesterday;
    }

    private List<MenuItem> availableMenu() {
        List<MenuItem> menu = menuRepository.findAll();
        if (menu.stream().noneMatch(MenuItem::isAvailable)) {
            throw new IllegalStateException(
                    "No available menu products. Import the menu first.");
        }
        return menu;
    }

    private SyntheticOrderGenerator generator(List<MenuItem> menu, long seed) {
        return new SyntheticOrderGenerator(
                menu,
                weatherRepository.findAllByOrderByObservedAtAsc(),
                eventRepository.findAllByOrderByStartsAtAsc(),
                seed);
    }

    private Saved save(List<GeneratedOrder> orders) {
        int confirmed = 0;
        int lines = 0;
        BigDecimal revenue = BigDecimal.ZERO;

        for (int i = 0; i < orders.size(); i++) {
            GeneratedOrder generated = orders.get(i);
            // Orders store Bremen local time, like orders from the website.
            CustomerOrder order = CustomerOrder.synthetic(
                    generated.reference(),
                    LocalDateTime.ofInstant(generated.createdAt(), SyntheticScenario.ZONE));

            for (Line line : generated.lines()) {
                order.addItem(new OrderItem(order, line.item(), line.quantity()));
            }
            order.recalculateTotal();

            // No staff account handled simulated orders, so handledBy stays empty.
            if (generated.cancelled()) {
                order.cancel(null, generated.handledAt());
            } else {
                order.confirm(null, generated.handledAt());
                confirmed++;
                revenue = revenue.add(generated.total());
            }
            lines += generated.lines().size();

            entityManager.persist(order);
            if ((i + 1) % FLUSH_EVERY == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }

        return new Saved(confirmed, lines, revenue);
    }

    /** Checks every generated order before anything is saved. */
    static void validate(List<GeneratedOrder> orders, LocalDate start, LocalDate end) {
        Set<UUID> references = new HashSet<>();
        for (GeneratedOrder order : orders) {
            ZonedDateTime local = order.createdAt().atZone(SyntheticScenario.ZONE);
            LocalDate day = local.toLocalDate();
            LocalTime time = local.toLocalTime();

            require(!day.isBefore(start) && !day.isAfter(end),
                    order, "is outside the date range");
            LocalTime opens = CafeCalendar.opens(day).orElse(null);
            require(opens != null, order, "is on a closed day");
            require(!time.isBefore(opens) && time.isBefore(CafeCalendar.lastOrderBefore()),
                    order, "is outside the opening hours");
            require(!order.lines().isEmpty(), order, "has no lines");
            require(order.lines().stream().allMatch(l -> l.quantity() > 0),
                    order, "has a quantity below 1");
            require(order.lines().stream().allMatch(l -> l.item().isAvailable()),
                    order, "contains an unavailable product");
            require(order.total().signum() > 0, order, "has no positive total");
            require(order.handledAt() != null && order.handledAt().isAfter(order.createdAt()),
                    order, "is handled before it was placed");
            require(references.add(order.reference()), order, "has a duplicate reference");
        }
    }

    private static void require(boolean condition, GeneratedOrder order, String problem) {
        if (!condition) {
            throw new IllegalStateException("Generated order " + order.reference()
                    + " at " + order.createdAt() + " " + problem + ".");
        }
    }
}
