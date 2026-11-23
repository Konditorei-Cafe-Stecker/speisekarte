package com.stecker.backend.synthetic;

import com.stecker.backend.context.ContextDataImporter;
import com.stecker.backend.dto.CreateOrderRequest;
import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.model.DataSource;
import com.stecker.backend.model.MenuItem;
import com.stecker.backend.model.OrderStatus;
import com.stecker.backend.repository.CustomerOrderRepository;
import com.stecker.backend.repository.LocalEventRepository;
import com.stecker.backend.repository.MenuRepository;
import com.stecker.backend.repository.WeatherObservationRepository;
import com.stecker.backend.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Saves a week of synthetic orders into the H2 test database. */
@SpringBootTest
@ActiveProfiles("test")
class SyntheticOrderServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 4, 13);
    private static final LocalDate END = LocalDate.of(2026, 4, 19);

    @Autowired private SyntheticOrderService syntheticOrderService;
    @Autowired private ContextDataImporter contextImporter;
    @Autowired private OrderService orderService;
    @Autowired private CustomerOrderRepository orderRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private WeatherObservationRepository weatherRepository;
    @Autowired private LocalEventRepository eventRepository;

    private List<MenuItem> menu;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        menu = menuRepository.saveAll(TestMenu.load());
        contextImporter.importAll();
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        menuRepository.deleteAll(menu);
        weatherRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
    }

    @Test
    void savesSyntheticOrdersAndNeverTouchesRealOnes() {
        CustomerOrder real = orderService.createOrder(new CreateOrderRequest(List.of(
                new CreateOrderRequest.OrderItemRequest(menu.get(0).getId(), 1))));

        SyntheticOrderService.Summary first = syntheticOrderService.generate(START, END, 7L, false);

        assertTrue(first.orders() > 150, "orders: " + first.orders());
        assertEquals(first.orders(), orderRepository.countByDataSource(DataSource.SYNTHETIC));
        assertEquals(1, orderRepository.countByDataSource(DataSource.REAL));

        // A second run without replace is refused.
        assertThrows(IllegalStateException.class,
                () -> syntheticOrderService.generate(START, END, 7L, false));

        // With replace, the same seed gives exactly the same orders again.
        UUID someReference = orderRepository.findAll().stream()
                .filter(o -> o.getDataSource() == DataSource.SYNTHETIC)
                .findFirst().orElseThrow().getOrderReference();
        SyntheticOrderService.Summary second = syntheticOrderService.generate(START, END, 7L, true);

        assertEquals(first.orders(), second.deletedOrders());
        assertEquals(first.orders(), second.orders());
        assertEquals(first.confirmedRevenue(), second.confirmedRevenue());
        assertTrue(orderRepository.findByOrderReference(someReference).isPresent());

        // The real order is still there, unchanged.
        CustomerOrder stillReal = orderRepository
                .findByOrderReference(real.getOrderReference()).orElseThrow();
        assertEquals(DataSource.REAL, stillReal.getDataSource());
        assertEquals(OrderStatus.PENDING, stillReal.getStatus());
        assertEquals(1, orderRepository.countByDataSource(DataSource.REAL));
    }

    @Test
    void continuesTheHistoryAsOneLongRunWouldHave() {
        orderService.createOrder(new CreateOrderRequest(List.of(
                new CreateOrderRequest.OrderItemRequest(menu.get(0).getId(), 1))));
        SyntheticOrderService.Summary week = syntheticOrderService.generate(START, END, 7L, false);

        // On Thursday 23 April: Monday to Wednesday are added.
        LocalDate thursday = LocalDate.of(2026, 4, 23);
        SyntheticOrderService.Continuation added =
                syntheticOrderService.continueHistory(7L, thursday).orElseThrow();

        assertEquals(LocalDate.of(2026, 4, 20), added.from());
        assertEquals(LocalDate.of(2026, 4, 22), added.to());
        assertTrue(added.orders() > 60, "orders: " + added.orders());
        assertEquals(week.orders() + added.orders(),
                orderRepository.countByDataSource(DataSource.SYNTHETIC));

        // Nothing more the same day.
        assertEquals(Optional.empty(), syntheticOrderService.continueHistory(7L, thursday));

        // Exactly the orders of one run over the whole time.
        Set<UUID> continued = syntheticReferences();
        syntheticOrderService.generate(START, LocalDate.of(2026, 4, 22), 7L, true);
        assertEquals(syntheticReferences(), continued);
        assertEquals(1, orderRepository.countByDataSource(DataSource.REAL));
    }

    @Test
    void doesNotStartAHistoryOfItsOwn() {
        assertEquals(Optional.empty(),
                syntheticOrderService.continueHistory(7L, LocalDate.of(2026, 4, 23)));
        assertEquals(0, orderRepository.countByDataSource(DataSource.SYNTHETIC));
    }

    @Test
    void endsWithTheLastDayThatHasWeatherUntilClosing() {
        LocalDate monday = LocalDate.of(2026, 11, 23);

        // 01:00 in Bremen on Monday: all of Sunday is measured.
        assertEquals(LocalDate.of(2026, 11, 22), SyntheticOrderService.lastCompleteDay(
                monday, Instant.parse("2026-11-23T00:00:00Z")));
        // Weather only up to Friday 17:00 (the last opening hour): Friday.
        assertEquals(LocalDate.of(2026, 11, 20), SyntheticOrderService.lastCompleteDay(
                monday, Instant.parse("2026-11-20T16:00:00Z")));
        // Up to Friday 16:00: Friday is incomplete, so Thursday.
        assertEquals(LocalDate.of(2026, 11, 19), SyntheticOrderService.lastCompleteDay(
                monday, Instant.parse("2026-11-20T15:00:00Z")));
    }

    private Set<UUID> syntheticReferences() {
        return orderRepository.findAll().stream()
                .filter(o -> o.getDataSource() == DataSource.SYNTHETIC)
                .map(CustomerOrder::getOrderReference)
                .collect(Collectors.toSet());
    }

    @Test
    void refusesHistoryThatReachesToday() {
        LocalDate today = LocalDate.now(SyntheticScenario.ZONE);

        assertThrows(IllegalArgumentException.class,
                () -> syntheticOrderService.generate(today.minusDays(3), today, 1L, false));
    }
}
