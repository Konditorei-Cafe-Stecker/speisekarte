package com.stecker.backend.controller;

import com.stecker.backend.dto.CreateOrderRequest;
import com.stecker.backend.dto.OrderResponse;
import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.service.OrderService;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Guest places an order from the menu. */
    @PostMapping
    public OrderResponse createOrder(
            @RequestBody CreateOrderRequest request
    ) {
        CustomerOrder order = orderService.createOrder(request);
        return OrderResponse.from(order);
    }

    /** Waiter opens an order from its QR code. */
    @GetMapping("/{orderReference}")
    public OrderResponse getOrder(
            @PathVariable UUID orderReference
    ) {
        return orderService.getOrder(orderReference);
    }

    /** Waiter replaces the lines with the final products and quantities. */
    @PutMapping("/{orderReference}/items")
    public OrderResponse updateItems(
            @PathVariable UUID orderReference,
            @RequestBody CreateOrderRequest request
    ) {
        return orderService.updateItems(orderReference, request);
    }

    /** Waiter confirms the order was entered into the official till. */
    @PostMapping("/{orderReference}/confirm")
    public OrderResponse confirmOrder(
            @PathVariable UUID orderReference
    ) {
        return orderService.confirmOrder(orderReference);
    }

    /** Waiter cancels the order. */
    @PostMapping("/{orderReference}/cancel")
    public OrderResponse cancelOrder(
            @PathVariable UUID orderReference
    ) {
        return orderService.cancelOrder(orderReference);
    }
}
