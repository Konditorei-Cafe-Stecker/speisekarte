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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/orders} through the whole application: validation,
 * error format, the order service and the database created by the
 * Flyway migrations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    private long filterkaffee;
    private long cappuccino;
    private long soldOut;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        menuRepository.deleteAll();

        filterkaffee = save("Tasse Filterkaffee", "3.50", true);
        cappuccino = save("Cappuccino mit Milch", "4.10", true);
        soldOut = save("Erdbeertorte", "5.80", false);
    }

    @Test
    void savesAnOrderAndAnswersCreated() throws Exception {
        mockMvc.perform(order("""
                        {"items": [
                          {"menuItemId": %d, "quantity": 1},
                          {"menuItemId": %d, "quantity": 2}
                        ]}""".formatted(filterkaffee, cappuccino)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/orders/")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalPrice").value(11.70))
                .andExpect(jsonPath("$.orderReference").isString())
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].productName").value("Cappuccino mit Milch"))
                .andExpect(jsonPath("$.items[1].unitPrice").value(4.10))
                .andExpect(jsonPath("$.items[1].quantity").value(2))
                .andExpect(jsonPath("$.items[1].lineTotal").value(8.20))
                .andExpect(jsonPath("$.items[1].menuItemId").value(cappuccino));

        assertEquals(1, orderRepository.count());
        assertEquals(2, orderItemRepository.count());
    }

    @Test
    void ignoresPricesSentByTheBrowser() throws Exception {
        mockMvc.perform(order("""
                        {"items": [{"menuItemId": %d, "quantity": 1, "unitPrice": 0.01}],
                         "totalPrice": 0.01}""".formatted(filterkaffee)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice").value(3.50));
    }

    @Test
    void answersAnEmptyOrderWithAGermanProblem() throws Exception {
        mockMvc.perform(order("{\"items\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Die Bestellung enthält keine Artikel."))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, orderRepository.count());
    }

    @Test
    void rejectsQuantitiesOutsideOneTo99() throws Exception {
        mockMvc.perform(order("""
                        {"items": [{"menuItemId": %d, "quantity": 0}]}""".formatted(filterkaffee)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem("Die Anzahl muss mindestens 1 sein.")));

        mockMvc.perform(order("""
                        {"items": [{"menuItemId": %d, "quantity": 100}]}""".formatted(filterkaffee)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem("Die Anzahl darf höchstens 99 sein.")));

        assertEquals(0, orderRepository.count());
    }

    @Test
    void answersUnreadableJsonWithoutInternalDetails() throws Exception {
        mockMvc.perform(order("nonsense"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Die Anfrage konnte nicht gelesen werden."))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void answersUnknownAndUnavailableProducts() throws Exception {
        mockMvc.perform(order("{\"items\": [{\"menuItemId\": 999999, \"quantity\": 1}]}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("nicht auf der Speisekarte")));

        mockMvc.perform(order("""
                        {"items": [{"menuItemId": %d, "quantity": 1}]}""".formatted(soldOut)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Erdbeertorte ist derzeit nicht verfügbar."));

        assertEquals(0, orderRepository.count());
    }

    private long save(String name, String price, boolean available) {
        return menuRepository
                .save(new MenuItem(null, name, null, new BigDecimal(price), "Test", available))
                .getId();
    }

    private static org.springframework.test.web.servlet.RequestBuilder order(String json) {
        return post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }
}
