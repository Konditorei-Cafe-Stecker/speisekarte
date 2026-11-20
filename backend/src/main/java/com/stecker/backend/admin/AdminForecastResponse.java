package com.stecker.backend.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Everything the admin dashboard shows for a planning period. */
public record AdminForecastResponse(
        Instant generatedAt,
        String basis,
        LocalDate from,
        LocalDate to,
        Staffing staffing,
        Summary summary,
        List<Day> days,
        List<ProductNeed> products
) {

    /** The staffing assumption used for the plan; the boss can change it. */
    public record Staffing(double ordersPerStaffHour, int minStaff) {
    }

    public record Summary(
            double expectedOrders,
            BigDecimal expectedRevenue,
            BigDecimal revenueLow,
            BigDecimal revenueHigh,
            int staffHours,
            LocalDate busiestDay
    ) {
    }

    public record Day(
            LocalDate day,
            double expectedOrders,
            int ordersLow,
            int ordersHigh,
            BigDecimal expectedRevenue,
            BigDecimal revenueLow,
            BigDecimal revenueHigh,
            Double tempMin,
            Double tempMax,
            int rainyHours,
            List<String> events,
            List<String> offers,
            int staffHours,
            int peakStaff,
            List<Hour> hours
    ) {
    }

    public record Hour(int hour, double expectedOrders, int ordersHigh, int staff) {
    }

    /**
     * Expected units for the whole period, and the "busy case" amount:
     * in 9 of 10 such periods demand should not exceed it.
     */
    public record ProductNeed(
            Long menuItemId,
            String product,
            String category,
            String subcategory,
            double expectedUnits,
            int busyCaseUnits,
            BigDecimal expectedRevenue
    ) {
    }
}
