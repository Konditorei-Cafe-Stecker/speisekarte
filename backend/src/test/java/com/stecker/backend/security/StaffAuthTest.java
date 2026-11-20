package com.stecker.backend.security;

import com.stecker.backend.model.StaffAccount;
import com.stecker.backend.model.StaffRole;
import com.stecker.backend.repository.StaffAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signing in, sessions, roles and account management through the real
 * security filters: form sign-in, session cookie and CSRF token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffAuthTest {

    private static final String ADMIN_PASSWORD = "chefin-test-passwort";
    private static final String STAFF_PASSWORD = "kellner-test-passwort";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffAccountRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptLimiter limiter;

    private long adminId;
    private long staffId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        limiter.clear();
        adminId = save("chefin", "Chefin", StaffRole.ADMIN, ADMIN_PASSWORD);
        staffId = save("kellner", "Kellner", StaffRole.STAFF, STAFF_PASSWORD);
    }

    @Test
    void signsInAndReturnsThePerson() throws Exception {
        MvcResult result = mockMvc.perform(login("Chefin ", ADMIN_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("chefin"))
                .andExpect(jsonPath("$.displayName").value("Chefin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn();

        MockHttpSession session = session(result);
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("chefin"));

        assertNotNull(repository.findById(adminId).orElseThrow().getLastLoginAt());
    }

    @Test
    void givesTheSameAnswerForWrongPasswordUnknownNameAndDeactivatedAccount() throws Exception {
        StaffAccount staff = repository.findById(staffId).orElseThrow();
        staff.setActive(false);
        repository.save(staff);

        for (var attempt : new String[][] {
                {"chefin", "falsches-passwort"},
                {"niemand", "irgendein-passwort"},
                {"kellner", STAFF_PASSWORD}}) {
            mockMvc.perform(login(attempt[0], attempt[1]))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Benutzername oder Passwort ist falsch."));
        }
    }

    @Test
    void answersRequestsWithoutSessionWith401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Bitte melde dich an."));

        mockMvc.perform(get("/api/admin/staff"))
                .andExpect(status().isUnauthorized());

        // Endpoints not listed in the security rules are closed.
        mockMvc.perform(get("/api/irgendwas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresTheCsrfTokenForSigningIn() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .param("username", "chefin")
                        .param("password", ADMIN_PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("Sitzung ist abgelaufen")));
    }

    @Test
    void keepsAdminEndpointsForAdmins() throws Exception {
        MockHttpSession staff = signIn("kellner", STAFF_PASSWORD);
        mockMvc.perform(get("/api/admin/staff").session(staff))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Dafür fehlt dir die Berechtigung."));

        MockHttpSession admin = signIn("chefin", ADMIN_PASSWORD);
        mockMvc.perform(get("/api/admin/staff").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[1].passwordHash").doesNotExist());
    }

    @Test
    void newAccountMustChooseItsOwnPasswordFirst() throws Exception {
        MockHttpSession admin = signIn("chefin", ADMIN_PASSWORD);
        mockMvc.perform(post("/api/admin/staff").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": " Anna.M ", "displayName": "Anna",
                                 "role": "STAFF", "temporaryPassword": "vorlaeufig-123"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/admin/staff/")))
                .andExpect(jsonPath("$.username").value("anna.m"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        MvcResult result = mockMvc.perform(login("anna.m", "vorlaeufig-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn();
        MockHttpSession anna = session(result);

        mockMvc.perform(get("/api/staff/orders").session(anna))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Bitte lege zuerst ein eigenes Passwort fest."));

        // The public site stays usable meanwhile.
        mockMvc.perform(get("/api/menu").session(anna)).andExpect(status().isOk());

        mockMvc.perform(changeOwnPassword(anna, "vorlaeufig-123", "zu-kurz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Das Passwort muss mindestens 10 Zeichen lang sein."));

        mockMvc.perform(changeOwnPassword(anna, "falsch-falsch", "annas-eigenes-passwort"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Das bisherige Passwort stimmt nicht."));

        mockMvc.perform(changeOwnPassword(anna, "vorlaeufig-123", "annas-eigenes-passwort"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        // The same session continues, now with access to staff endpoints.
        mockMvc.perform(get("/api/auth/me").session(anna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
        mockMvc.perform(get("/api/staff/orders").session(anna))
                .andExpect(status().isOk());
    }

    @Test
    void deactivatingAnAccountEndsItsSession() throws Exception {
        MockHttpSession staff = signIn("kellner", STAFF_PASSWORD);
        MockHttpSession admin = signIn("chefin", ADMIN_PASSWORD);

        mockMvc.perform(updateStaff(admin, staffId, "{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/auth/me").session(staff))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resettingAPasswordEndsSessionsAndRequiresANewOne() throws Exception {
        MockHttpSession staff = signIn("kellner", STAFF_PASSWORD);
        MockHttpSession admin = signIn("chefin", ADMIN_PASSWORD);

        mockMvc.perform(post("/api/admin/staff/{id}/password", staffId)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temporaryPassword\": \"neu-vorlaeufig-1\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(staff))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(login("kellner", STAFF_PASSWORD))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(login("kellner", "neu-vorlaeufig-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void keepsAtLeastOneActiveAdmin() throws Exception {
        MockHttpSession admin = signIn("chefin", ADMIN_PASSWORD);

        mockMvc.perform(updateStaff(admin, adminId, "{\"active\": false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Du kannst dein eigenes Konto nicht deaktivieren."));
        mockMvc.perform(updateStaff(admin, adminId, "{\"role\": \"STAFF\"}"))
                .andExpect(status().isConflict());

        // A second admin may demote the first, but not the last one.
        mockMvc.perform(updateStaff(admin, staffId, "{\"role\": \"ADMIN\"}"))
                .andExpect(status().isOk());
        MockHttpSession second = signIn("kellner", STAFF_PASSWORD);
        mockMvc.perform(updateStaff(second, adminId, "{\"role\": \"STAFF\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(updateStaff(second, adminId, "{\"role\": \"ADMIN\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(updateStaff(admin, staffId, "{\"active\": false}"))
                .andExpect(status().isOk());
        assertEquals(1, repository.countByRoleAndActiveTrue(StaffRole.ADMIN));
    }

    @Test
    void validatesNewAccounts() throws Exception {
        MockHttpSession admin = signIn("chefin", ADMIN_PASSWORD);

        mockMvc.perform(createStaff(admin, "kellner", "lang-genug-123"))
                .andExpect(status().isConflict());
        mockMvc.perform(createStaff(admin, "a b", "lang-genug-123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Benutzername")));
        mockMvc.perform(createStaff(admin, "neu", "kurz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Das Passwort muss mindestens 10 Zeichen lang sein."));
        mockMvc.perform(post("/api/admin/staff").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        assertEquals(2, repository.count());
    }

    @Test
    void blocksSigningInAfterFiveFailures() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(login("kellner", "falsch-" + attempt))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login("kellner", STAFF_PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail", containsString("15 Minuten")));

        // Other people can still sign in.
        mockMvc.perform(login("chefin", ADMIN_PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void signsOut() throws Exception {
        MockHttpSession admin = signIn("chefin", ADMIN_PASSWORD);

        mockMvc.perform(post("/api/auth/logout").session(admin).with(csrf()))
                .andExpect(status().isNoContent());

        assertTrue(admin.isInvalid());
    }

    private long save(String username, String displayName, StaffRole role, String password) {
        StaffAccount account = new StaffAccount(
                username, displayName, passwordEncoder.encode(password), role, Instant.now());
        account.changePassword(account.getPasswordHash(), false, Instant.now().minusSeconds(60));
        return repository.save(account).getId();
    }

    private MockHttpSession signIn(String username, String password) throws Exception {
        return session(mockMvc.perform(login(username, password))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static MockHttpSession session(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static org.springframework.test.web.servlet.RequestBuilder login(
            String username, String password) {
        return post("/api/auth/login")
                .param("username", username)
                .param("password", password)
                .with(csrf());
    }

    private static org.springframework.test.web.servlet.RequestBuilder changeOwnPassword(
            MockHttpSession session, String current, String next) {
        return post("/api/auth/password").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}"
                        .formatted(current, next));
    }

    private static org.springframework.test.web.servlet.RequestBuilder updateStaff(
            MockHttpSession session, long id, String json) {
        return patch("/api/admin/staff/{id}", id).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private static org.springframework.test.web.servlet.RequestBuilder createStaff(
            MockHttpSession session, String username, String password) {
        return post("/api/admin/staff").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "displayName": "Neu", "role": "STAFF",
                         "temporaryPassword": "%s"}""".formatted(username, password));
    }
}
