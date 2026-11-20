package com.stecker.backend.service;

import com.stecker.backend.dto.CreateOrderRequest;
import com.stecker.backend.dto.CreateOrderRequest.OrderItemRequest;
import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.model.DataSource;
import com.stecker.backend.model.MenuItem;
import com.stecker.backend.model.OrderStatus;
import com.stecker.backend.repository.CustomerOrderRepository;
import com.stecker.backend.repository.MenuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private CustomerOrderRepository orderRepository;
    private MenuRepository menuRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(CustomerOrderRepository.class);
        menuRepository = mock(MenuRepository.class);
        orderService = new OrderService(orderRepository, menuRepository);

        // save() returns the order it was given, like the real repository.
        when(orderRepository.save(any(CustomerOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createOrderUsesDatabasePricesAndMarksOrderAsRealAndPending() {
        MenuItem filterkaffee = new MenuItem(
                1L, "Tasse Filterkaffee", null,
                new BigDecimal("3.50"), "Getränke", true);
        MenuItem cappuccino = new MenuItem(
                6L, "Cappuccino mit Milch", null,
                new BigDecimal("4.10"), "Getränke", true);

        when(menuRepository.findById(1L)).thenReturn(Optional.of(filterkaffee));
        when(menuRepository.findById(6L)).thenReturn(Optional.of(cappuccino));

        Instant before = Instant.now();

        CustomerOrder order = orderService.createOrder(new CreateOrderRequest(List.of(
                new OrderItemRequest(1L, 1),
                new OrderItemRequest(6L, 2)
        )));

        // The README's example: 1 × 3,50 € + 2 × 4,10 € = 11,70 €
        assertEquals(new BigDecimal("11.70"), order.getTotalPrice());
        assertEquals(2, order.getItems().size());
        assertEquals(new BigDecimal("8.20"), order.getItems().get(1).getLineTotal());
        assertEquals("Cappuccino mit Milch", order.getItems().get(1).getProductName());

        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(DataSource.REAL, order.getDataSource());
        assertNotNull(order.getOrderReference());
        assertNull(order.getConfirmedAt());

        Instant createdAt = order.getCreatedAt();
        assertNotNull(createdAt);
        assertTrue(!createdAt.isBefore(before)
                && Duration.between(createdAt, Instant.now()).toSeconds() < 5);

        verify(orderRepository).save(order);
    }

    @Test
    void createOrderRejectsEmptyOrder() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(new CreateOrderRequest(List.of())));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderRejectsZeroQuantity() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(new CreateOrderRequest(List.of(
                        new OrderItemRequest(1L, 0)))));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderRejectsUnknownProduct() {
        when(menuRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(new CreateOrderRequest(List.of(
                        new OrderItemRequest(999L, 1)))));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderRejectsUnavailableProduct() {
        MenuItem soldOut = new MenuItem(
                95L, "Erdbeere Stück", null,
                new BigDecimal("5.80"), "Eis & Desserts", false);
        when(menuRepository.findById(95L)).thenReturn(Optional.of(soldOut));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(new CreateOrderRequest(List.of(
                        new OrderItemRequest(95L, 1)))));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void confirmOrderRecordsConfirmationTime() {
        Instant fixedNow = Instant.parse("2026-11-20T14:30:00Z");
        OrderService serviceWithClock = new OrderService(
                orderRepository, menuRepository,
                Clock.fixed(fixedNow, ZoneOffset.UTC));

        CustomerOrder order = new CustomerOrder(new BigDecimal("3.30"));
        when(orderRepository.findByOrderReference(order.getOrderReference()))
                .thenReturn(Optional.of(order));

        var response = serviceWithClock.confirmOrder(order.getOrderReference());

        assertEquals(OrderStatus.CONFIRMED, response.status());
        assertEquals(fixedNow, response.confirmedAt());
        assertEquals(fixedNow, order.getConfirmedAt());
    }

    @Test
    void cancelledOrderCannotBeConfirmed() {
        CustomerOrder order = new CustomerOrder(new BigDecimal("3.30"));
        order.cancel();
        when(orderRepository.findByOrderReference(order.getOrderReference()))
                .thenReturn(Optional.of(order));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> orderService.confirmOrder(order.getOrderReference()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertNull(order.getConfirmedAt());
    }

    @Test
    void unknownOrderReferenceIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(orderRepository.findByOrderReference(unknown))
                .thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> orderService.getOrder(unknown));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }
}
