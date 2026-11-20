package com.stecker.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A guest's order as sent by the Speisekarte.
 *
 * The messages are German because they can be shown to guests and staff.
 */
public record CreateOrderRequest(
        @NotEmpty(message = "Die Bestellung enthält keine Artikel.")
        @Size(max = OrderLimits.MAX_LINES,
                message = "Eine Bestellung kann höchstens "
                        + OrderLimits.MAX_LINES + " verschiedene Artikel enthalten.")
        List<@NotNull(message = "Ein Artikel der Bestellung fehlt.")
                @Valid OrderItemRequest> items,

        /** Optional wishes for the waiter, for example "ohne Sahne". */
        @Size(max = OrderLimits.MAX_NOTE_LENGTH,
                message = "Die Wünsche dürfen höchstens "
                        + OrderLimits.MAX_NOTE_LENGTH + " Zeichen lang sein.")
        String note
) {

    /** An order without wishes. */
    public CreateOrderRequest(List<OrderItemRequest> items) {
        this(items, null);
    }

    public record OrderItemRequest(
            @NotNull(message = "Bei einem Artikel fehlt die Produktnummer.")
            Long menuItemId,

            @Min(value = 1, message = "Die Anzahl muss mindestens 1 sein.")
            @Max(value = OrderLimits.MAX_QUANTITY,
                    message = "Die Anzahl darf höchstens "
                            + OrderLimits.MAX_QUANTITY + " sein.")
            int quantity
    ) {
    }

    /** Upper limits that keep a single order reasonable. */
    public static final class OrderLimits {
        public static final int MAX_LINES = 50;
        public static final int MAX_QUANTITY = 99;
        public static final int MAX_NOTE_LENGTH = 300;

        private OrderLimits() {
        }
    }
}
