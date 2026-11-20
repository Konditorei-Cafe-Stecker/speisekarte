package com.stecker.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A guest's order.
 *
 * It starts as PENDING. A waiter checks it with the guest, enters it into
 * the café's till system and then confirms it here (CONFIRMED), possibly
 * with changed quantities, or cancels it (CANCELLED). After that it can no
 * longer change.
 */
@Entity
@Table(name = "customer_orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Long random id; knowing it is what gives a guest access to the order. */
    @Column(nullable = false, unique = true, updatable = false)
    private UUID orderReference;

    /** Local time in Bremen (Europe/Berlin) when the guest sent the order. */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    /**
     * REAL for orders placed through the website, SYNTHETIC for the
     * simulated history used by the analysis. Reports keep them apart.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private DataSource dataSource = DataSource.REAL;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    /** Short code for reading out; empty for orders saved before it existed. */
    @Column(length = 8)
    private String shortCode;

    @Column(length = 300)
    private String guestNote;

    private Instant handledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by_id")
    private StaffAccount handledBy;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(
        mappedBy = "customerOrder",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(String shortCode, String guestNote, LocalDateTime createdAt) {
        this.orderReference = UUID.randomUUID();
        this.createdAt = createdAt;
        this.status = OrderStatus.PENDING;
        this.totalPrice = BigDecimal.ZERO;
        this.shortCode = shortCode;
        this.guestNote = guestNote;
    }

    /**
     * An order for the simulated history. Only the synthetic data generator
     * calls this.
     *
     * @param orderReference reproducible reference derived from the seed
     * @param createdAt      simulated local time in Bremen
     */
    public static CustomerOrder synthetic(UUID orderReference, LocalDateTime createdAt) {
        CustomerOrder order = new CustomerOrder(null, null, createdAt);
        order.orderReference = orderReference;
        order.dataSource = DataSource.SYNTHETIC;
        return order;
    }

    public void addItem(OrderItem item) {
        items.add(item);
    }

    /** Adds up the lines again, after quantities changed. */
    public void recalculateTotal() {
        totalPrice = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isOpen() {
        return status == OrderStatus.PENDING;
    }

    public void confirm(StaffAccount staff, Instant now) {
        close(OrderStatus.CONFIRMED, staff, now);
    }

    public void cancel(StaffAccount staff, Instant now) {
        close(OrderStatus.CANCELLED, staff, now);
    }

    private void close(OrderStatus newStatus, StaffAccount staff, Instant now) {
        this.status = newStatus;
        this.handledBy = staff;
        this.handledAt = now;
    }

    public Long getId() {
        return id;
    }

    public UUID getOrderReference() {
        return orderReference;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getGuestNote() {
        return guestNote;
    }

    public Instant getHandledAt() {
        return handledAt;
    }

    public StaffAccount getHandledBy() {
        return handledBy;
    }

    public List<OrderItem> getItems() {
        return items;
    }
}
