package com.stecker.backend.security;

import com.stecker.backend.model.StaffAccount;
import com.stecker.backend.model.StaffRole;
import com.stecker.backend.repository.StaffAccountRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The CSRF flow exactly as the staff area uses it: read the token from the
 * {@code XSRF-TOKEN} cookie and send it back in the {@code X-XSRF-TOKEN}
 * header.
 *
 * Runs in its own application context (the extra property forces that) and
 * never uses the test helper {@code csrf()}, which would replace the real
 * token store of the shared filter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "stecker.test.context=csrf-cookie-flow")
class CsrfCookieFlowTest {

    private static final String PASSWORD = "chefin-test-passwort";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffAccountRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        StaffAccount account = new StaffAccount(
                "chefin", "Chefin", passwordEncoder.encode(PASSWORD), StaffRole.ADMIN, Instant.now());
        account.changePassword(account.getPasswordHash(), false, Instant.now().minusSeconds(60));
        repository.save(account);
    }

    @Test
    void signsInWithTheTokenFromTheCookie() throws Exception {
        MvcResult tokenResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.cookieName").value("XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();

        Cookie cookie = tokenResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie, "XSRF-TOKEN cookie");
        assertFalse(cookie.isHttpOnly(), "the staff area reads it from JavaScript");

        // Wrong token: refused.
        mockMvc.perform(post("/api/auth/login")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", "falsch")
                        .param("username", "chefin")
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden());

        // Token from the cookie in the header: signed in.
        mockMvc.perform(post("/api/auth/login")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", cookie.getValue())
                        .param("username", "chefin")
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("chefin"));
    }
}
