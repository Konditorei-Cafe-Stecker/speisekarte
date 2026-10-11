package com.stecker.backend.service;

import com.stecker.backend.model.MenuItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MenuService {

    public List<MenuItem> getMenuItems() {
        return List.of(
                new MenuItem(
                        1L,
                        "Cappuccino",
                        "Espresso with steamed milk",
                        new BigDecimal("3.50"),
                        "Coffee",
                        true
                ),
                new MenuItem(
                        2L,
                        "Käsekuchen",
                        "Classic German cheesecake",
                        new BigDecimal("4.20"),
                        "Cake",
                        true
                ),
                new MenuItem(
                        3L,
                        "Buttercroissant",
                        "Freshly baked butter croissant",
                        new BigDecimal("2.50"),
                        "Bakery",
                        true
                )
        );
    }
}