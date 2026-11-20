package com.stecker.backend.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Without STECKER_ADMIN_TOKEN, the admin API stays closed for everyone. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDisabledTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void adminApiIsOffWithoutAConfiguredToken() throws Exception {
        mockMvc.perform(get("/api/admin/forecast").header("X-Admin-Token", ""))
                .andExpect(status().isServiceUnavailable());
    }
}
