package com.stecker.backend.dto;

import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.model.OrderItem;
import com.stecker.backend.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderReference,
        LocalDateTime createdAt,
        OrderStatus status,
        BigDecimal totalPrice,
        List<OrderItemResponse> items
) {

    public record OrderItemResponse(
            Long menuItemId,
            String productName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal
    ) {
    }

    public static OrderResponse from(CustomerOrder order) {
        List<OrderItemResponse> items = order.getItems()
                .stream()
                .map(item -> new OrderItemResponse(
                        item.getMenuItem().getId(),
                        item.getProductName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getLineTotal()
                ))
                .toList();

        return new OrderResponse(
                order.getOrderReference(),
                order.getCreatedAt(),
                order.getStatus(),
                order.getTotalPrice(),
                items
        );
    }
}
