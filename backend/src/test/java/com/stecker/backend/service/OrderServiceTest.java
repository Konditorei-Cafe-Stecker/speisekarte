package com.stecker.backend.service;

import com.stecker.backend.dto.CreateOrderRequest;
import com.stecker.backend.dto.CreateOrderRequest.OrderItemRequest;
import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.model.MenuItem;
import com.stecker.backend.model.OrderStatus;
import com.stecker.backend.repository.CustomerOrderRepository;
import com.stecker.backend.repository.MenuRepository;
import com.stecker.backend.repository.StaffAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private static final MenuItem FILTERKAFFEE = new MenuItem(
            1L, "Tasse Filterkaffee", null, new BigDecimal("3.50"), "Getränke", true);
    private static final MenuItem CAPPUCCINO = new MenuItem(
            5L, "Cappuccino mit Milch", null, new BigDecimal("4.10"), "Getränke", true);
    private static final MenuItem SOLD_OUT = new MenuItem(
            9L, "Erdbeertorte", null, new BigDecimal("5.80"), "Speisen", false);

    private CustomerOrderRepository orderRepository;
    private MenuRepository menuRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(CustomerOrderRepository.class);
        menuRepository = mock(MenuRepository.class);
        orderService = new OrderService(
                orderRepository,
                menuRepository,
                mock(StaffAccountRepository.class),
                Clock.fixed(Instant.parse("2026-11-11T10:15:00Z"), ZoneOffset.UTC));

        when(orderRepository.save(any(CustomerOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(menuRepository.findById(1L)).thenReturn(Optional.of(FILTERKAFFEE));
        when(menuRepository.findById(5L)).thenReturn(Optional.of(CAPPUCCINO));
        when(menuRepository.findById(9L)).thenReturn(Optional.of(SOLD_OUT));
        when(menuRepository.findById(404L)).thenReturn(Optional.empty());
    }

    @Test
    void savesPendingOrderWithPricesFromTheMenu() {
        CustomerOrder order = orderService.createOrder(request(
                new OrderItemRequest(1L, 1),
                new OrderItemRequest(5L, 2)));

        // The README's manual check: 3,50 € + 2 × 4,10 € = 11,70 €.
        assertEquals(new BigDecimal("11.70"), order.getTotalPrice());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertNotNull(order.getOrderReference());
        // Stored as local time in Bremen (UTC+1 in November).
        assertEquals(LocalDateTime.parse("2026-11-11T11:15:00"), order.getCreatedAt());
        assertTrue(order.getShortCode().matches("[ACDEFHJKLMNPRTUVWXY3479]{4}"));
        assertEquals(2, order.getItems().size());
        assertEquals("Cappuccino mit Milch", order.getItems().get(1).getProductName());
        assertEquals(new BigDecimal("8.20"), order.getItems().get(1).getLineTotal());
        verify(orderRepository).save(order);
    }

    @Test
    void addsUpTheSameProductListedTwice() {
        CustomerOrder order = orderService.createOrder(request(
                new OrderItemRequest(5L, 1),
                new OrderItemRequest(1L, 1),
                new OrderItemRequest(5L, 2)));

        assertEquals(2, order.getItems().size());
        assertEquals(3, order.getItems().getFirst().getQuantity());
        assertEquals(new BigDecimal("15.80"), order.getTotalPrice());
    }

    @Test
    void rejectsAnEmptyOrder() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> orderService.createOrder(new CreateOrderRequest(List.of())));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("Die Bestellung enthält keine Artikel.", error.getReason());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void rejectsAnUnknownProduct() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> orderService.createOrder(request(new OrderItemRequest(404L, 1))));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void rejectsAnUnavailableProduct() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> orderService.createOrder(request(new OrderItemRequest(9L, 1))));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(error.getReason().startsWith("Erdbeertorte"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void rejectsMoreThan99OfOneProductAcrossLines() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> orderService.createOrder(request(
                        new OrderItemRequest(1L, 60),
                        new OrderItemRequest(1L, 40))));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(orderRepository, never()).save(any());
    }

    private static CreateOrderRequest request(OrderItemRequest... items) {
        return new CreateOrderRequest(List.of(items));
    }
}
