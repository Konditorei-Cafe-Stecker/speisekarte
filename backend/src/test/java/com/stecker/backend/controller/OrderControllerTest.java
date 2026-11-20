package com.stecker.backend.controller;

import com.jayway.jsonpath.JsonPath;
import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.model.DataSource;
import com.stecker.backend.model.MenuItem;
import com.stecker.backend.model.OrderStatus;
import com.stecker.backend.repository.CustomerOrderRepository;
import com.stecker.backend.repository.MenuRepository;
import com.stecker.backend.repository.OrderItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sends real HTTP requests through the order endpoints and checks what
 * ends up in the (in-memory H2) database. Each request runs in its own
 * transaction, like in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    private Long filterkaffeeId;
    private Long cappuccinoId;
    private Long espressoId;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        filterkaffeeId = menuRepository.save(new MenuItem(
                null, "Tasse Filterkaffee", null,
                new BigDecimal("3.50"), "Getränke", true)).getId();
        cappuccinoId = menuRepository.save(new MenuItem(
                null, "Cappuccino mit Milch", null,
                new BigDecimal("4.10"), "Getränke", true)).getId();
        espressoId = menuRepository.save(new MenuItem(
                null, "Espresso", null,
                new BigDecimal("3.30"), "Getränke", true)).getId();
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        menuRepository.deleteAllById(
                java.util.List.of(filterkaffeeId, cappuccinoId, espressoId));
    }

    @Test
    void waiterCanOpenEditAndConfirmAnOrder() throws Exception {
        // Guest: 1 Filterkaffee + 2 Cappuccino = 11,70 €
        String reference = placeOrder("""
                {"items": [
                  {"menuItemId": %d, "quantity": 1},
                  {"menuItemId": %d, "quantity": 2}
                ]}""".formatted(filterkaffeeId, cappuccinoId));

        // Waiter scans the QR and opens the order.
        mockMvc.perform(get("/api/orders/" + reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalPrice").value(11.70))
                .andExpect(jsonPath("$.confirmedAt").doesNotExist())
                .andExpect(jsonPath("$.items.length()").value(2));

        // Guest changes their mind: 1 Cappuccino + 1 Espresso = 7,40 €
        mockMvc.perform(put("/api/orders/" + reference + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [
                                  {"menuItemId": %d, "quantity": 1},
                                  {"menuItemId": %d, "quantity": 1}
                                ]}""".formatted(cappuccinoId, espressoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(7.40))
                .andExpect(jsonPath("$.items[0].productName")
                        .value("Cappuccino mit Milch"))
                .andExpect(jsonPath("$.items[1].productName")
                        .value("Espresso"));

        // The old lines are gone from the database, not just hidden.
        assertEquals(2, orderItemRepository.count());

        // Waiter entered it into the till and confirms.
        mockMvc.perform(post("/api/orders/" + reference + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty());

        CustomerOrder stored = orderRepository
                .findByOrderReference(UUID.fromString(reference))
                .orElseThrow();
        assertEquals(OrderStatus.CONFIRMED, stored.getStatus());
        assertEquals(DataSource.REAL, stored.getDataSource());
        assertEquals(new BigDecimal("7.40"), stored.getTotalPrice());
        assertNotNull(stored.getConfirmedAt());
    }

    @Test
    void confirmedOrderCannotBeChangedConfirmedAgainOrCancelled()
            throws Exception {
        String reference = placeOrder("""
                {"items": [{"menuItemId": %d, "quantity": 1}]}"""
                .formatted(espressoId));

        mockMvc.perform(post("/api/orders/" + reference + "/confirm"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/" + reference + "/confirm"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/orders/" + reference + "/cancel"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/orders/" + reference + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"menuItemId": %d, "quantity": 5}]}"""
                                .formatted(espressoId)))
                .andExpect(status().isConflict());
    }

    @Test
    void pendingOrderCanBeCancelled() throws Exception {
        String reference = placeOrder("""
                {"items": [{"menuItemId": %d, "quantity": 1}]}"""
                .formatted(espressoId));

        mockMvc.perform(post("/api/orders/" + reference + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.confirmedAt").doesNotExist());
    }

    @Test
    void unknownOrderReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/orders/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/" + UUID.randomUUID() + "/confirm"))
                .andExpect(status().isNotFound());
    }

    private String placeOrder(String json) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(body, "$.orderReference");
    }
}
