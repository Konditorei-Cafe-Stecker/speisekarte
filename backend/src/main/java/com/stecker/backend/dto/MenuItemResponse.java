package com.stecker.backend.dto;

import com.stecker.backend.model.MenuItem;

import java.math.BigDecimal;

/**
 * One product of the Speisekarte as returned by {@code GET /api/menu}.
 *
 * The frontend depends on exactly these fields. Keeping them in their own
 * type means a new column in the database does not change the API by
 * accident.
 */
public record MenuItemResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String category,
        String subcategory,
        String sizeVolume,
        String notes,
        boolean available
) {

    public static MenuItemResponse from(MenuItem item) {
        return new MenuItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                item.getCategory(),
                item.getSubcategory(),
                item.getSizeVolume(),
                item.getNotes(),
                item.isAvailable()
        );
    }
}
