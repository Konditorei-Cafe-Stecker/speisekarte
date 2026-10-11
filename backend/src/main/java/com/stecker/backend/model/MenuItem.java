package com.stecker.backend.model;
import java.math.BigDecimal;

public class MenuItem {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private boolean available;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCategory() {
        return category;
    }

    public boolean isAvailable() {
        return available;
    }

    public MenuItem(
            Long id,
            String name,
            String description,
            BigDecimal price,
            String category,
            boolean available
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.available = available;
    }
}
