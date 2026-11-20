package com.stecker.backend.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Admin API for the owner's planning dashboard.
 * Every request needs the X-Admin-Token header (see AdminTokenInterceptor).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private final AdminForecastService forecastService;
    private final double defaultOrdersPerStaffHour;
    private final int defaultMinStaff;

    public AdminController(
            AdminForecastService forecastService,
            @Value("${stecker.admin.staff.orders-per-staff-hour:6}") double defaultOrdersPerStaffHour,
            @Value("${stecker.admin.staff.min-staff:2}") int defaultMinStaff
    ) {
        this.forecastService = forecastService;
        this.defaultOrdersPerStaffHour = defaultOrdersPerStaffHour;
        this.defaultMinStaff = defaultMinStaff;
    }

    /** Forecast revenue, staffing and product needs, starting today by default. */
    @GetMapping("/forecast")
    public AdminForecastResponse forecast(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) Double ordersPerStaffHour,
            @RequestParam(required = false) Integer minStaff
    ) {
        double perStaff = ordersPerStaffHour != null ? ordersPerStaffHour : defaultOrdersPerStaffHour;
        int minimum = minStaff != null ? minStaff : defaultMinStaff;
        if (days < 1 || days > 14) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be 1–14.");
        }
        if (perStaff < 1 || perStaff > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ordersPerStaffHour must be between 1 and 50.");
        }
        if (minimum < 0 || minimum > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "minStaff must be between 0 and 20.");
        }
        return forecastService.forecast(
                from != null ? from : LocalDate.now(BERLIN),
                days,
                new AdminForecastResponse.Staffing(perStaff, minimum));
    }
}
