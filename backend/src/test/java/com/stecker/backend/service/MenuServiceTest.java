package com.stecker.backend.service;

import com.stecker.backend.model.MenuItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuServiceTest {

    @Test
    void getMenuItemsReturnsExpectedProducts() {
        MenuService menuService = new MenuService();

        List<MenuItem> menuItems = menuService.getMenuItems();

        assertEquals(3, menuItems.size());
        assertEquals("Cappuccino", menuItems.get(0).getName());
        assertEquals(new BigDecimal("3.50"), menuItems.get(0).getPrice());
        assertTrue(menuItems.get(0).isAvailable());
    }
}
