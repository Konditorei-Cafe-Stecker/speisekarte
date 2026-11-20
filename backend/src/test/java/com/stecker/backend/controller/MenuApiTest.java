package com.stecker.backend.controller;

import com.stecker.backend.model.MenuItem;
import com.stecker.backend.repository.CustomerOrderRepository;
import com.stecker.backend.repository.MenuRepository;
import com.stecker.backend.repository.OrderItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /api/menu} keeps exactly the fields the frontend reads. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MenuApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        menuRepository.deleteAll();

        menuRepository.save(new MenuItem(
                null, "Tasse Filterkaffee", null, new BigDecimal("3.50"), "Getränke", true));
        menuRepository.save(new MenuItem(
                null, "Erdbeertorte", "Mit Sahne", new BigDecimal("5.80"), "Speisen", false));
    }

    @Test
    void returnsAllProductsWithTheAgreedFields() throws Exception {
        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Tasse Filterkaffee"))
                .andExpect(jsonPath("$[0].price").value(3.50))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[1].available").value(false))
                .andExpect(jsonPath("$[1].description").value("Mit Sahne"))
                .andExpect(jsonPath("$[0].keys()", containsInAnyOrder(
                        "id", "name", "description", "price", "category",
                        "subcategory", "sizeVolume", "notes", "available")));
    }
}
