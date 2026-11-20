package com.stecker.backend.admin;

import com.stecker.backend.model.ForecastDay;
import com.stecker.backend.model.StaffAccount;
import com.stecker.backend.model.StaffRole;
import com.stecker.backend.repository.StaffAccountRepository;
import com.stecker.backend.security.LoginAttemptLimiter;
import com.stecker.backend.model.ForecastHour;
import com.stecker.backend.model.ForecastProduct;
import com.stecker.backend.model.MenuItem;
import com.stecker.backend.repository.MenuRepository;
import com.stecker.backend.repository.ForecastDayRepository;
import com.stecker.backend.repository.ForecastHourRepository;
import com.stecker.backend.repository.ForecastProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The owner's planning API: admins only, through the real sign-in. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 23);

    @Autowired private MockMvc mockMvc;
    @Autowired private ForecastDayRepository dayRepository;
    @Autowired private ForecastHourRepository hourRepository;
    @Autowired private ForecastProductRepository productRepository;
    @Autowired private StaffAccountRepository staffRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptLimiter limiter;

    private MockHttpSession admin;
    private List<MenuItem> menu;

    @BeforeEach
    void setUp() throws Exception {
        staffRepository.deleteAll();
        limiter.clear();
        saveAccount("chefin", StaffRole.ADMIN, "chefin-test-passwort");
        saveAccount("kellner", StaffRole.STAFF, "kellner-test-passwort");
        admin = signIn("chefin", "chefin-test-passwort");
        menu = menuRepository.saveAll(List.of(
                new MenuItem(null, "Tasse Filterkaffee", null, new BigDecimal("3.50"), "Getränke", true),
                new MenuItem(null, "Glühwein", null, new BigDecimal("5.50"), "Getränke", true)));
        Long kaffee = menu.get(0).getId();
        Long gluehwein = menu.get(1).getId();

        Instant generated = Instant.parse("2026-11-22T06:00:00Z");
        dayRepository.saveAll(List.of(
                new ForecastDay(MONDAY, 45.2, 37, 54, new BigDecimal("690.00"),
                        new BigDecimal("565.00"), new BigDecimal("824.00"), -1.0, 3.0, 0,
                        "Bremer Weihnachtsmarkt 2026", "Glühwein offer | hot drinks",
                        "SYNTHETIC", generated),
                new ForecastDay(MONDAY.plusDays(1), 46.0, 38, 55, new BigDecimal("700.00"),
                        new BigDecimal("578.00"), new BigDecimal("837.00"), -2.0, 2.0, 1,
                        null, null, "SYNTHETIC", generated)));
        hourRepository.saveAll(List.of(
                new ForecastHour(MONDAY, 9, 2.1, 4),
                new ForecastHour(MONDAY, 15, 8.3, 13),
                new ForecastHour(MONDAY.plusDays(1), 15, 8.0, 12)));
        productRepository.saveAll(List.of(
                new ForecastProduct(MONDAY, kaffee, "Tasse Filterkaffee", "Getränke", "Azul Kaffee",
                        new BigDecimal("3.50"), 10.0),
                new ForecastProduct(MONDAY.plusDays(1), kaffee, "Tasse Filterkaffee", "Getränke",
                        "Azul Kaffee", new BigDecimal("3.50"), 12.0),
                new ForecastProduct(MONDAY, gluehwein, "Glühwein", "Getränke", "Zum Aufwärmen",
                        new BigDecimal("5.50"), 4.0)));
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
        hourRepository.deleteAll();
        dayRepository.deleteAll();
        menuRepository.deleteAll(menu);
        staffRepository.deleteAll();
    }

    @Test
    void isOnlyForSignedInAdmins() throws Exception {
        mockMvc.perform(get("/api/admin/forecast"))
                .andExpect(status().isUnauthorized());

        MockHttpSession waiter = signIn("kellner", "kellner-test-passwort");
        mockMvc.perform(get("/api/admin/forecast").session(waiter))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsRevenueStaffingAndProductNeeds() throws Exception {
        mockMvc.perform(get("/api/admin/forecast")
                        .session(admin)
                        .param("from", MONDAY.toString())
                        .param("days", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("SYNTHETIC"))
                .andExpect(jsonPath("$.summary.expectedRevenue").value(1390.00))
                // 91.2 orders expected; P10 = 79, P90 = 104 → revenue scaled by 79/91.2, 104/91.2
                .andExpect(jsonPath("$.summary.revenueLow").value(1204.06))
                .andExpect(jsonPath("$.summary.revenueHigh").value(1585.09))
                .andExpect(jsonPath("$.summary.busiestDay").value("2026-11-24"))
                .andExpect(jsonPath("$.staffing.ordersPerStaffHour").value(6.0))
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].events[0]").value("Bremer Weihnachtsmarkt 2026"))
                .andExpect(jsonPath("$.days[0].offers.length()").value(2))
                // 13 busy-case orders at 15:00 / 6 per person → 3 staff; 9:00 → minimum 2
                .andExpect(jsonPath("$.days[0].hours[0].staff").value(2))
                .andExpect(jsonPath("$.days[0].hours[1].staff").value(3))
                .andExpect(jsonPath("$.days[0].staffHours").value(5))
                .andExpect(jsonPath("$.products[0].product").value("Tasse Filterkaffee"))
                .andExpect(jsonPath("$.products[0].expectedUnits").value(22.0))
                .andExpect(jsonPath("$.products[0].busyCaseUnits").value(28))
                .andExpect(jsonPath("$.products[0].expectedRevenue").value(77.00));
    }

    @Test
    void staffingAssumptionCanBeChanged() throws Exception {
        mockMvc.perform(get("/api/admin/forecast")
                        .session(admin)
                        .param("from", MONDAY.toString())
                        .param("days", "1")
                        .param("ordersPerStaffHour", "4")
                        .param("minStaff", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].hours[0].staff").value(1))
                .andExpect(jsonPath("$.days[0].hours[1].staff").value(4));
    }

    @Test
    void explainsWhenNoForecastIsPublished() throws Exception {
        mockMvc.perform(get("/api/admin/forecast")
                        .session(admin)
                        .param("from", "2030-01-01"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnreasonableSettings() throws Exception {
        mockMvc.perform(get("/api/admin/forecast")
                        .session(admin)
                        .param("days", "60"))
                .andExpect(status().isBadRequest());
    }

    private void saveAccount(String username, StaffRole role, String password) {
        StaffAccount account = new StaffAccount(
                username, username, passwordEncoder.encode(password), role, Instant.now());
        account.changePassword(account.getPasswordHash(), false, Instant.now().minusSeconds(60));
        staffRepository.save(account);
    }

    private MockHttpSession signIn(String username, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .param("username", username)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }
}
