package com.stecker.backend.dto;

import java.util.List;

public record CreateOrderRequest(
        List<OrderItemRequest> items
) {

    public record OrderItemRequest(
            Long menuItemId,
            int quantity
    ) {
    }
}
