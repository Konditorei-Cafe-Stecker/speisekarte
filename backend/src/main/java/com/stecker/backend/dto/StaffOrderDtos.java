package com.stecker.backend.dto;

import com.stecker.backend.dto.CreateOrderRequest.OrderLimits;
import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.model.OrderItem;
import com.stecker.backend.model.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Orders as the waiter works with them. */
public final class StaffOrderDtos {

    private StaffOrderDtos() {
    }

    /** One order in the lists of open and handled orders. */
    public record OrderSummary(
            UUID orderReference,
            String shortCode,
            LocalDateTime createdAt,
            OrderStatus status,
            BigDecimal totalPrice,
            int itemCount,
            boolean hasNote,
            Instant handledAt,
            String handledBy
    ) {
        public static OrderSummary from(CustomerOrder order) {
            return new OrderSummary(
                    order.getOrderReference(),
                    order.getShortCode(),
                    order.getCreatedAt(),
                    order.getStatus(),
                    order.getTotalPrice(),
                    order.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
                    order.getGuestNote() != null && !order.getGuestNote().isBlank(),
                    order.getHandledAt(),
                    order.getHandledBy() == null ? null : order.getHandledBy().getDisplayName());
        }
    }

    /** The full order, including what the guest originally asked for. */
    public record OrderDetail(
            UUID orderReference,
            String shortCode,
            LocalDateTime createdAt,
            OrderStatus status,
            String note,
            BigDecimal totalPrice,
            Instant handledAt,
            String handledBy,
            List<Line> items
    ) {
        public record Line(
                Long menuItemId,
                String productName,
                BigDecimal unitPrice,
                int quantity,
                int originalQuantity,
                BigDecimal lineTotal
        ) {
        }

        public static OrderDetail from(CustomerOrder order) {
            return new OrderDetail(
                    order.getOrderReference(),
                    order.getShortCode(),
                    order.getCreatedAt(),
                    order.getStatus(),
                    order.getGuestNote(),
                    order.getTotalPrice(),
                    order.getHandledAt(),
                    order.getHandledBy() == null ? null : order.getHandledBy().getDisplayName(),
                    order.getItems().stream()
                            .map(item -> new Line(
                                    item.getMenuItem().getId(),
                                    item.getProductName(),
                                    item.getUnitPrice(),
                                    item.getQuantity(),
                                    item.getOriginalQuantity(),
                                    item.getLineTotal()))
                            .toList());
        }
    }

    /**
     * Confirms an order. Without {@code items} it is confirmed as it is.
     * With {@code items}, that list is the final order: listed products get
     * the given quantity (0 removes them), products not listed are removed,
     * and new products are added at today's menu price.
     */
    public record ConfirmOrderRequest(
            @Size(max = OrderLimits.MAX_LINES,
                    message = "Eine Bestellung kann höchstens "
                            + OrderLimits.MAX_LINES + " verschiedene Artikel enthalten.")
            List<@NotNull(message = "Ein Artikel der Bestellung fehlt.") @Valid FinalLine> items
    ) {
        public record FinalLine(
                @NotNull(message = "Bei einem Artikel fehlt die Produktnummer.")
                Long menuItemId,

                @Min(value = 0, message = "Die Anzahl darf nicht negativ sein.")
                @Max(value = OrderLimits.MAX_QUANTITY,
                        message = "Die Anzahl darf höchstens "
                                + OrderLimits.MAX_QUANTITY + " sein.")
                int quantity
        ) {
        }
    }
}
