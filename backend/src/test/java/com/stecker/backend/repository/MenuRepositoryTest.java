package com.stecker.backend.repository;

import com.stecker.backend.model.MenuItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
@ActiveProfiles("test")
class MenuRepositoryTest {

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesAndReloadsMenuItemWithGeneratedId() {
        MenuItem espresso = new MenuItem(
                null,
                "Espresso",
                "Small espresso",
                new BigDecimal("2.80"),
                "Coffee",
                false
        );

        MenuItem saved = menuRepository.saveAndFlush(espresso);
        Long generatedId = saved.getId();

        assertNotNull(generatedId);

        entityManager.clear();

        MenuItem reloaded = menuRepository.findById(generatedId)
                .orElseThrow();

        assertEquals("Espresso", reloaded.getName());
        assertEquals("Small espresso", reloaded.getDescription());
        assertEquals(new BigDecimal("2.80"), reloaded.getPrice());
        assertEquals("Coffee", reloaded.getCategory());
        assertFalse(reloaded.isAvailable());
    }

    @Test
    void returnsMenuItemsOrderedById() {
        MenuItem first = menuRepository.saveAndFlush(new MenuItem(
                null,
                "Zimtrolle",
                "Cinnamon pastry",
                new BigDecimal("3.20"),
                "Bakery",
                true
        ));

        MenuItem second = menuRepository.saveAndFlush(new MenuItem(
                null,
                "Cappuccino",
                "Coffee with milk",
                new BigDecimal("3.50"),
                "Coffee",
                true
        ));

        entityManager.clear();

        List<MenuItem> items = menuRepository.findAllByOrderByIdAsc();

        assertEquals(
                List.of(first.getId(), second.getId()),
                items.stream().map(MenuItem::getId).toList()
        );
    }
}
