package com.stecker.backend.controller;

import com.jayway.jsonpath.JsonPath;
import com.stecker.backend.model.MenuItem;
import com.stecker.backend.model.StaffAccount;
import com.stecker.backend.model.StaffRole;
import com.stecker.backend.repository.CustomerOrderRepository;
import com.stecker.backend.repository.MenuRepository;
import com.stecker.backend.repository.OrderItemRepository;
import com.stecker.backend.repository.StaffAccountRepository;
import com.stecker.backend.security.LoginAttemptLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The README's order workflow end to end: the guest orders, the waiter
 * finds the order, adjusts it and confirms or cancels it, the guest sees
 * the result.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderWorkflowTest {

    private static final String PASSWORD = "kellner-test-passwort";

    @Autowired private MockMvc mockMvc;
    @Autowired private MenuRepository menuRepository;
    @Autowired private CustomerOrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private StaffAccountRepository staffRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptLimiter limiter;

    private long kaffee;
    private long cappuccino;
    private long torte;
    private long soldOut;
    private Cookie waiter;

    @BeforeEach
    void setUp() throws Exception {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        menuRepository.deleteAll();
        staffRepository.deleteAll();
        limiter.clear();

        kaffee = menu("Tasse Filterkaffee", "3.50", true);
        cappuccino = menu("Cappuccino mit Milch", "4.10", true);
        torte = menu("Schneemustorte Erdbeere", "5.80", true);
        soldOut = menu("Eiskaffee", "6.90", false);

        StaffAccount account = new StaffAccount(
                "kellner", "Kellner Ben", passwordEncoder.encode(PASSWORD), StaffRole.STAFF, Instant.now());
        account.changePassword(account.getPasswordHash(), false, Instant.now().minusSeconds(60));
        staffRepository.save(account);

        waiter = sessionCookie(mockMvc.perform(post("/api/auth/login")
                        .param("username", "kellner").param("password", PASSWORD).with(csrf()))
                .andExpect(status().isOk())
                .andReturn());
    }

    @Test
    void guestOrdersWithANoteAndSeesTheStatus() throws Exception {
        String reference = order("""
                {"items": [{"menuItemId": %d, "quantity": 2}],
                 "note": "  Bitte ohne Sahne  "}""".formatted(cappuccino));

        mockMvc.perform(get("/api/orders/{ref}", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.note").value("Bitte ohne Sahne"))
                .andExpect(jsonPath("$.shortCode", matchesPattern("[ACDEFHJKLMNPRTUVWXY3479]{4}")))
                .andExpect(jsonPath("$.totalPrice").value(8.20))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.handledBy").doesNotExist());
    }

    @Test
    void answersUnknownAndMalformedOrderNumbersWith404() throws Exception {
        mockMvc.perform(get("/api/orders/{ref}", "6f1c2a52-0000-4000-8000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Diese Bestellung gibt es nicht."));

        mockMvc.perform(get("/api/orders/kein-uuid"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsTooLongNotes() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"menuItemId": %d, "quantity": 1}], "note": "%s"}"""
                                .formatted(kaffee, "x".repeat(301))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die Wünsche dürfen höchstens 300 Zeichen lang sein."));
    }

    @Test
    void keepsStaffEndpointsForStaff() throws Exception {
        mockMvc.perform(get("/api/staff/orders")).andExpect(status().isUnauthorized());

        String reference = order(simpleOrder());
        mockMvc.perform(post("/api/staff/orders/{ref}/confirm", reference).cookie(waiter))
                .andExpect(status().isForbidden()); // no CSRF token
    }

    @Test
    void waiterFindsOpenOrdersByListAndByCode() throws Exception {
        String first = order(simpleOrder());
        String second = order("""
                {"items": [{"menuItemId": %d, "quantity": 1}], "note": "Tisch am Fenster"}"""
                .formatted(torte));

        mockMvc.perform(get("/api/staff/orders").cookie(waiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].orderReference").value(first))
                .andExpect(jsonPath("$[0].itemCount").value(3))
                .andExpect(jsonPath("$[1].hasNote").value(true));

        String code = JsonPath.read(mockMvc.perform(get("/api/orders/{ref}", second))
                .andReturn().getResponse().getContentAsString(), "$.shortCode");

        mockMvc.perform(get("/api/staff/orders/code/{code}", " " + code.toLowerCase() + " ").cookie(waiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderReference").value(second))
                .andExpect(jsonPath("$.note").value("Tisch am Fenster"));

        mockMvc.perform(get("/api/staff/orders/code/{code}", "QQQQ").cookie(waiter))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString("QQQQ")));
    }

    @Test
    void waiterAdjustsAndConfirmsTheGuestSeesTheResult() throws Exception {
        // Guest: 1 Filterkaffee, 2 Cappuccino = 11,70 €.
        String reference = order(simpleOrder());

        // Waiter: no Filterkaffee after all, 1 Cappuccino, plus a Torte.
        mockMvc.perform(confirm(reference, """
                        {"items": [
                          {"menuItemId": %d, "quantity": 1},
                          {"menuItemId": %d, "quantity": 1}
                        ]}""".formatted(cappuccino, torte)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.handledBy").value("Kellner Ben"))
                .andExpect(jsonPath("$.handledAt").isString())
                .andExpect(jsonPath("$.totalPrice").value(9.90))
                .andExpect(jsonPath("$.items.length()").value(3))
                // What the guest asked for is kept for later analysis.
                .andExpect(jsonPath("$.items[0].productName").value("Tasse Filterkaffee"))
                .andExpect(jsonPath("$.items[0].quantity").value(0))
                .andExpect(jsonPath("$.items[0].originalQuantity").value(1))
                .andExpect(jsonPath("$.items[1].quantity").value(1))
                .andExpect(jsonPath("$.items[1].originalQuantity").value(2))
                .andExpect(jsonPath("$.items[2].productName").value("Schneemustorte Erdbeere"))
                .andExpect(jsonPath("$.items[2].originalQuantity").value(0));

        // The guest sees only what was confirmed.
        mockMvc.perform(get("/api/orders/{ref}", reference))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalPrice").value(9.90))
                .andExpect(jsonPath("$.items.length()").value(2));

        // Confirmed orders are no longer open, and cannot change.
        mockMvc.perform(get("/api/staff/orders").cookie(waiter))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/staff/orders?view=today").cookie(waiter))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].handledBy").value("Kellner Ben"));
        mockMvc.perform(confirm(reference, "{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Diese Bestellung wurde bereits von Kellner Ben bestätigt."));
        mockMvc.perform(post("/api/staff/orders/{ref}/cancel", reference).cookie(waiter).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmsUnchangedWithoutABody() throws Exception {
        String reference = order(simpleOrder());

        mockMvc.perform(post("/api/staff/orders/{ref}/confirm", reference).cookie(waiter).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(11.70));
    }

    @Test
    void refusesUnavailableAdditionsAndEmptyConfirmations() throws Exception {
        String reference = order(simpleOrder());

        mockMvc.perform(confirm(reference, """
                        {"items": [{"menuItemId": %d, "quantity": 1}]}""".formatted(soldOut)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Eiskaffee ist derzeit nicht verfügbar."));

        mockMvc.perform(confirm(reference, "{\"items\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Storniere sie stattdessen")));

        // Nothing was changed by the refused attempts.
        mockMvc.perform(get("/api/orders/{ref}", reference))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalPrice").value(11.70));
    }

    @Test
    void waiterCancels() throws Exception {
        String reference = order(simpleOrder());

        mockMvc.perform(post("/api/staff/orders/{ref}/cancel", reference).cookie(waiter).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.handledBy").value("Kellner Ben"));

        mockMvc.perform(get("/api/orders/{ref}", reference))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(confirm(reference, "{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("storniert")));
    }

    private String simpleOrder() {
        return """
                {"items": [
                  {"menuItemId": %d, "quantity": 1},
                  {"menuItemId": %d, "quantity": 2}
                ]}""".formatted(kaffee, cappuccino);
    }

    private String order(String json) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.orderReference");
    }

    private RequestBuilder confirm(String reference, String json) {
        return post("/api/staff/orders/{ref}/confirm", reference)
                .cookie(waiter).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private long menu(String name, String price, boolean available) {
        return menuRepository
                .save(new MenuItem(null, name, null, new BigDecimal(price), "Test", available))
                .getId();
    }

    /** The session cookie the browser would keep after signing in. */
    private static Cookie sessionCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertNotNull(cookie, "no session cookie after sign-in");
        return cookie;
    }
}
