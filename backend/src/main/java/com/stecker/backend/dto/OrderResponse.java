package com.stecker.backend.dto;

import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * An order as the guest sees it: after sending, and while waiting for the
 * waiter. Lines the waiter removed are left out; who handled the order is
 * not shown.
 */
public record OrderResponse(
        UUID orderReference,
        String shortCode,
        LocalDateTime createdAt,
        OrderStatus status,
        String note,
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
                .filter(item -> item.getQuantity() > 0)
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
                order.getShortCode(),
                order.getCreatedAt(),
                order.getStatus(),
                order.getGuestNote(),
                order.getTotalPrice(),
                items
        );
    }
}
