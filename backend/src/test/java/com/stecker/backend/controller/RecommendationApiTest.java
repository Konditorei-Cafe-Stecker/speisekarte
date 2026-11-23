package com.stecker.backend.controller;

import com.stecker.backend.model.ForecastDay;
import com.stecker.backend.model.MenuItem;
import com.stecker.backend.repository.CustomerOrderRepository;
import com.stecker.backend.repository.ForecastDayRepository;
import com.stecker.backend.repository.ForecastHourRepository;
import com.stecker.backend.repository.ForecastProductRepository;
import com.stecker.backend.repository.MenuRepository;
import com.stecker.backend.repository.OrderItemRepository;
import com.stecker.backend.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /api/recommendations/today}: public, from today's forecast. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MenuRepository menuRepository;
    @Autowired private ForecastDayRepository forecastDays;
    @Autowired private ForecastHourRepository forecastHours;
    @Autowired private ForecastProductRepository forecastProducts;
    @Autowired private CustomerOrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    private final LocalDate today = LocalDate.now(OrderService.CAFE_ZONE);

    @BeforeEach
    void setUp() {
        forecastProducts.deleteAll();
        forecastHours.deleteAll();
        forecastDays.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        menuRepository.deleteAll();
    }

    @Test
    void returnsTodaysRecommendationsWithoutSignIn() throws Exception {
        long gluehwein = menuRepository.save(new MenuItem(
                null, "Glühwein", null, new BigDecimal("5.50"), "Getränke", true)).getId();
        forecastDays.save(new ForecastDay(today, 47, 38, 56, new BigDecimal("685.05"),
                new BigDecimal("550"), new BigDecimal("820"), -1.0, 3.0, 0, "",
                "Glühwein-Angebot (Weihnachtsmarkt) | Heißgetränke-Angebot (Schokolade, Tee)",
                "SYNTHETIC", Instant.now()));

        mockMvc.perform(get("/api/recommendations/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.day").value(today.toString()))
                // The warm-drink products are not on this test menu, so only
                // the Glühwein recommendation remains.
                .andExpect(jsonPath("$.recommendations.length()").value(1))
                .andExpect(jsonPath("$.recommendations[0].kind").value("CHRISTMAS_MARKET"))
                .andExpect(jsonPath("$.recommendations[0].title").value("Zur Weihnachtsmarkt-Zeit"))
                .andExpect(jsonPath("$.recommendations[0].products[0].id").value(gluehwein))
                .andExpect(jsonPath("$.recommendations[0].products[0].price").value(5.50));
    }

    @Test
    void answersWithAnEmptyListWithoutForecast() throws Exception {
        mockMvc.perform(get("/api/recommendations/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations.length()").value(0));
    }
}
