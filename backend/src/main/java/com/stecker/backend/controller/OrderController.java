package com.stecker.backend.controller;

import com.stecker.backend.dto.CreateOrderRequest;
import com.stecker.backend.dto.OrderResponse;
import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.service.OrderService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/** The guest's side of an order. Public: no sign-in. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Saves a guest's order. Answers 201 Created with the saved order. */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request
    ) {
        CustomerOrder order = orderService.createOrder(request);

        return ResponseEntity
                .created(URI.create("/api/orders/" + order.getOrderReference()))
                .body(OrderResponse.from(order));
    }

    /**
     * The current state of an order, for the guest's confirmation screen.
     * The random reference is long enough that only the guest knows it.
     */
    @GetMapping("/{reference}")
    public OrderResponse getOrder(@PathVariable UUID reference) {
        return orderService.getForGuest(reference);
    }
}
