package com.stecker.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Forecast for one opening day, written by analysis/forecast_and_offers.py
 * (--publish). The backend only reads it.
 */
@Entity
@Table(name = "forecast_days")
public class ForecastDay {

    @Id
    @Column(name = "forecast_day")
    private LocalDate day;

    @Column(nullable = false)
    private double expectedOrders;

    /** 10 % of days are expected to have fewer orders than this. */
    @Column(nullable = false)
    private int ordersLow;

    /** 90 % of days are expected to have at most this many orders. */
    @Column(nullable = false)
    private int ordersHigh;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal expectedRevenue;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal revenueLow;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal revenueHigh;

    @Column
    private Double tempMin;

    @Column
    private Double tempMax;

    @Column(nullable = false)
    private int rainyHours;

    /** Events that day, separated by " | ". */
    @Column(length = 1000)
    private String events;

    /** Suggested offers that day, separated by " | ". */
    @Column(length = 1000)
    private String offers;

    /** Which orders the model learned from: SYNTHETIC, REAL or ALL. */
    @Column(nullable = false, length = 20)
    private String basis;

    @Column(nullable = false)
    private Instant generatedAt;

    protected ForecastDay() {
    }

    public ForecastDay(LocalDate day, double expectedOrders, int ordersLow, int ordersHigh,
                       BigDecimal expectedRevenue, BigDecimal revenueLow, BigDecimal revenueHigh,
                       Double tempMin, Double tempMax, int rainyHours, String events,
                       String offers, String basis, Instant generatedAt) {
        this.day = day;
        this.expectedOrders = expectedOrders;
        this.ordersLow = ordersLow;
        this.ordersHigh = ordersHigh;
        this.expectedRevenue = expectedRevenue;
        this.revenueLow = revenueLow;
        this.revenueHigh = revenueHigh;
        this.tempMin = tempMin;
        this.tempMax = tempMax;
        this.rainyHours = rainyHours;
        this.events = events;
        this.offers = offers;
        this.basis = basis;
        this.generatedAt = generatedAt;
    }

    public LocalDate getDay() { return day; }
    public double getExpectedOrders() { return expectedOrders; }
    public int getOrdersLow() { return ordersLow; }
    public int getOrdersHigh() { return ordersHigh; }
    public BigDecimal getExpectedRevenue() { return expectedRevenue; }
    public BigDecimal getRevenueLow() { return revenueLow; }
    public BigDecimal getRevenueHigh() { return revenueHigh; }
    public Double getTempMin() { return tempMin; }
    public Double getTempMax() { return tempMax; }
    public int getRainyHours() { return rainyHours; }
    public String getEvents() { return events; }
    public String getOffers() { return offers; }
    public String getBasis() { return basis; }
    public Instant getGeneratedAt() { return generatedAt; }
}
