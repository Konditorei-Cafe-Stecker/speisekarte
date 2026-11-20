package com.stecker.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** Forecast orders for one opening hour (local time). */
@Entity
@Table(name = "forecast_hours",
        indexes = @Index(name = "idx_forecast_hours_day", columnList = "forecast_day"))
public class ForecastHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "forecast_day", nullable = false)
    private LocalDate day;

    /** Local hour the orders are placed in, e.g. 15 for 15:00–15:59. */
    @Column(name = "local_hour", nullable = false)
    private int hour;

    @Column(nullable = false)
    private double expectedOrders;

    /** 90 % of such hours are expected to have at most this many orders. */
    @Column(nullable = false)
    private int ordersHigh;

    protected ForecastHour() {
    }

    public ForecastHour(LocalDate day, int hour, double expectedOrders, int ordersHigh) {
        this.day = day;
        this.hour = hour;
        this.expectedOrders = expectedOrders;
        this.ordersHigh = ordersHigh;
    }

    public Long getId() { return id; }
    public LocalDate getDay() { return day; }
    public int getHour() { return hour; }
    public double getExpectedOrders() { return expectedOrders; }
    public int getOrdersHigh() { return ordersHigh; }
}
