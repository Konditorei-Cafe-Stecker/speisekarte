package com.stecker.backend.deploy;

import com.stecker.backend.model.StaffAccount;
import com.stecker.backend.model.StaffRole;
import com.stecker.backend.repository.StaffAccountRepository;
import com.stecker.backend.security.LoginAttemptLimiter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What running several backend copies (Kubernetes) relies on: health
 * probes, sessions stored in the database, and a cacheable menu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeveralCopiesTest {

    private static final String PASSWORD = "chefin-test-passwort";

    @Autowired private MockMvc mockMvc;
    @Autowired private StaffAccountRepository staffRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptLimiter limiter;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        staffRepository.deleteAll();
        limiter.clear();
        StaffAccount account = new StaffAccount("chefin", "Chefin",
                passwordEncoder.encode(PASSWORD), StaffRole.ADMIN, Instant.now());
        account.changePassword(account.getPasswordHash(), false, Instant.now().minusSeconds(60));
        staffRepository.save(account);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM SPRING_SESSION");
        staffRepository.deleteAll();
    }

    @Test
    void healthProbesAnswerWithoutSignIn() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        // No details about the database or disk for strangers.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void showsTheRunningVersion() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.version").isNotEmpty());
    }

    @Test
    void otherActuatorEndpointsStayClosed() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/actuator/beans")).andExpect(status().is4xxClientError());
    }

    @Test
    void signInIsStoredInTheDatabaseForEveryCopy() throws Exception {
        Cookie session = mockMvc.perform(post("/api/auth/login")
                        .param("username", "chefin")
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
        assertNotNull(session);

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = 'chefin'", Integer.class));

        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("chefin"));
    }

    @Test
    void browsersMayReuseTheMenuForHalfAMinute() throws Exception {
        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=30, public"));
    }
}
