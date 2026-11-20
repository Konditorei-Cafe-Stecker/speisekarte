package com.stecker.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customer_orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID orderReference;

    /**
     * The exact moment the order was placed, stored in UTC.
     * Reports convert it to Europe/Berlin time.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    /**
     * REAL for website orders, SYNTHETIC for generated history.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private DataSource dataSource;

    /**
     * When the waiter confirmed the order was entered into the
     * official till. Empty while the order is still pending.
     */
    @Column
    private Instant confirmedAt;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @OneToMany(
        mappedBy = "customerOrder",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    /**
     * Creates a new pending order placed through the website.
     */
    public CustomerOrder(BigDecimal totalPrice) {
        this.orderReference = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.status = OrderStatus.PENDING;
        this.dataSource = DataSource.REAL;
        this.totalPrice = totalPrice;
    }

    public void addItem(OrderItem item) {
        items.add(item);
    }

    /**
     * Replaces all order lines, for example after the waiter edits the
     * order with the guest. Removed lines are deleted from the database.
     */
    public void replaceItems(List<OrderItem> newItems, BigDecimal newTotal) {
        items.clear();
        items.addAll(newItems);
        this.totalPrice = newTotal;
    }

    /** Waiter entered the order into the official till. */
    public void confirm(Instant confirmedAt) {
        this.status = OrderStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
    }

    /** Order will not be served. */
    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public Long getId() {
        return id;
    }

    public UUID getOrderReference() {
        return orderReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
