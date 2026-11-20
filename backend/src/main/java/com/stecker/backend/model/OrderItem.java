package com.stecker.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * One product in an order. Name and price are copied from the menu when
 * the line is created, so later menu changes do not change the order.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder customerOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /** The confirmed quantity; 0 when the waiter removed the line. */
    @Column(nullable = false)
    private int quantity;

    /** What the guest ordered; 0 for lines the waiter added. */
    @Column(nullable = false)
    private int originalQuantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    protected OrderItem() {
    }

    /** A line ordered by the guest. */
    public OrderItem(
            CustomerOrder customerOrder,
            MenuItem menuItem,
            int quantity
    ) {
        this(customerOrder, menuItem, quantity, quantity);
    }

    private OrderItem(
            CustomerOrder customerOrder,
            MenuItem menuItem,
            int quantity,
            int originalQuantity
    ) {
        this.customerOrder = customerOrder;
        this.menuItem = menuItem;
        this.productName = menuItem.getName();
        this.unitPrice = menuItem.getPrice();
        this.originalQuantity = originalQuantity;
        setQuantity(quantity);
    }

    /** A line the waiter added while checking the order with the guest. */
    public static OrderItem addedByStaff(
            CustomerOrder customerOrder,
            MenuItem menuItem,
            int quantity
    ) {
        return new OrderItem(customerOrder, menuItem, quantity, 0);
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getId() {
        return id;
    }

    public CustomerOrder getCustomerOrder() {
        return customerOrder;
    }

    public MenuItem getMenuItem() {
        return menuItem;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getOriginalQuantity() {
        return originalQuantity;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
