package com.stecker.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Expected units of one product on one day. */
@Entity
@Table(name = "forecast_products")
public class ForecastProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "forecast_day", nullable = false)
    private LocalDate day;

    @Column(nullable = false)
    private Long menuItemId;

    /** Name with size, e.g. "Apollinaris Selection (0,75 l)". */
    @Column(nullable = false)
    private String productLabel;

    @Column(nullable = false)
    private String category;

    @Column
    private String subcategory;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private double expectedUnits;

    protected ForecastProduct() {
    }

    public ForecastProduct(LocalDate day, Long menuItemId, String productLabel, String category,
                           String subcategory, BigDecimal unitPrice, double expectedUnits) {
        this.day = day;
        this.menuItemId = menuItemId;
        this.productLabel = productLabel;
        this.category = category;
        this.subcategory = subcategory;
        this.unitPrice = unitPrice;
        this.expectedUnits = expectedUnits;
    }

    public Long getId() { return id; }
    public LocalDate getDay() { return day; }
    public Long getMenuItemId() { return menuItemId; }
    public String getProductLabel() { return productLabel; }
    public String getCategory() { return category; }
    public String getSubcategory() { return subcategory; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public double getExpectedUnits() { return expectedUnits; }
}
