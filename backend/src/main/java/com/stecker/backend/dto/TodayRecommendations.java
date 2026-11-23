package com.stecker.backend.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * What the website recommends to guests today, derived from the published
 * forecast. Prices are the normal menu prices; nothing is discounted.
 *
 * {@code recommendations} is empty when there is no forecast for today or
 * none of its suggestions applies. Recommendations whose time window is
 * over (breakfast after 12:00, say) are left out.
 */
public record TodayRecommendations(
        LocalDate day,
        List<Recommendation> recommendations
) {

    /**
     * @param kind       WARM_DRINKS, CHRISTMAS_MARKET, BREAKFAST, MATCH_DAY or
     *                   ICE_CREAM, for styling
     * @param title      short German heading, e.g. "Zum Aufwärmen"
     * @param text       one German sentence for guests
     * @param validFrom  local time in Bremen it starts, or null for all day
     * @param validUntil local time in Bremen it ends, or null for all day;
     *                   after it, the recommendation is no longer returned
     * @param products   the menu items it refers to, available ones only
     */
    public record Recommendation(
            String kind,
            String title,
            String text,
            LocalTime validFrom,
            LocalTime validUntil,
            List<MenuItemResponse> products
    ) {
    }
}
