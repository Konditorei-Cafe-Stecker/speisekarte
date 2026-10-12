package com.stecker.backend.service;

import com.stecker.backend.model.MenuItem;
import com.stecker.backend.repository.MenuRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MenuServiceTest {

    @Test
    void getMenuItemsReturnsRepositoryProducts() {
        MenuRepository menuRepository = mock(MenuRepository.class);

        MenuItem espresso = new MenuItem(
                42L,
                "Espresso",
                "Small espresso",
                new BigDecimal("2.80"),
                "Coffee",
                false
        );

        when(menuRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(espresso));

        MenuService menuService = new MenuService(menuRepository);

        List<MenuItem> menuItems = menuService.getMenuItems();

        assertEquals(1, menuItems.size());
        assertEquals("Espresso", menuItems.get(0).getName());
        assertEquals(new BigDecimal("2.80"), menuItems.get(0).getPrice());
        assertFalse(menuItems.get(0).isAvailable());

        verify(menuRepository).findAllByOrderByIdAsc();
    }

    @Test
    void getMenuItemsReturnsEmptyListWhenRepositoryIsEmpty() {
        MenuRepository menuRepository = mock(MenuRepository.class);

        when(menuRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of());

        MenuService menuService = new MenuService(menuRepository);

        assertTrue(menuService.getMenuItems().isEmpty());

        verify(menuRepository).findAllByOrderByIdAsc();
    }
}