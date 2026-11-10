package com.stecker.backend.controller;

import com.stecker.backend.dto.CreateOrderRequest;
import com.stecker.backend.dto.OrderResponse;
import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.service.OrderService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse createOrder(
            @RequestBody CreateOrderRequest request
    ) {
        CustomerOrder order = orderService.createOrder(request);
        return OrderResponse.from(order);
    }
}
