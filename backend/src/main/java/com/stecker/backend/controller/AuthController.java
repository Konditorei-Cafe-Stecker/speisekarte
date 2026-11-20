package com.stecker.backend.controller;

import com.stecker.backend.dto.StaffDtos.ChangeOwnPasswordRequest;
import com.stecker.backend.dto.StaffDtos.CurrentStaff;
import com.stecker.backend.repository.StaffAccountRepository;
import com.stecker.backend.security.StaffPrincipal;
import com.stecker.backend.service.StaffAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Sign-in support for the staff area. Signing in and out themselves are
 * handled by Spring Security at {@code POST /api/auth/login} (form fields
 * {@code username} and {@code password}) and {@code POST /api/auth/logout}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final StaffAccountService accounts;
    private final StaffAccountRepository repository;
    private final SecurityContextRepository contextRepository;

    public AuthController(
            StaffAccountService accounts,
            StaffAccountRepository repository,
            SecurityContextRepository contextRepository
    ) {
        this.accounts = accounts;
        this.repository = repository;
        this.contextRepository = contextRepository;
    }

    /**
     * Makes sure the {@code XSRF-TOKEN} cookie is set. The staff area sends
     * the cookie's value back in the header named here with every changing
     * request. It calls this when the cookie is missing, for example after
     * signing in or out, because the token changes then.
     *
     * The token is deliberately not in the body: that copy is masked and
     * only valid as a form field, not in the header.
     */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        token.getToken(); // creating the token writes the cookie
        return Map.of("headerName", token.getHeaderName(), "cookieName", "XSRF-TOKEN");
    }

    /** The signed-in person, or 401 when nobody is signed in. */
    @GetMapping("/me")
    public CurrentStaff me(@AuthenticationPrincipal StaffPrincipal principal) {
        return CurrentStaff.from(principal);
    }

    /** Chooses an own password. Other sessions of this person end. */
    @PostMapping("/password")
    public ResponseEntity<CurrentStaff> changePassword(
            @AuthenticationPrincipal StaffPrincipal principal,
            @Valid @RequestBody ChangeOwnPasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        Instant changedAt = accounts.changeOwnPassword(
                principal.id(), request.currentPassword(), request.newPassword());

        // This session stays valid: it now counts as started at the change.
        StaffPrincipal updated = principal.signedInAgainAt(
                changedAt, repository.findById(principal.id()).orElseThrow());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                updated, null, updated.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, httpRequest, httpResponse);

        return ResponseEntity.ok(CurrentStaff.from(updated));
    }
}
