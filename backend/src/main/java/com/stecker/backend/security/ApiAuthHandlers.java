package com.stecker.backend.security;

import com.stecker.backend.dto.StaffDtos.CurrentStaff;
import com.stecker.backend.service.StaffAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JSON answers for sign-in and for refused requests, instead of the
 * redirects to a login page that Spring Security sends by default.
 */
@Component
public class ApiAuthHandlers {

    private final StaffAccountService accounts;
    private final LoginAttemptLimiter limiter;
    private final ProblemResponses problems;

    public ApiAuthHandlers(
            StaffAccountService accounts,
            LoginAttemptLimiter limiter,
            ProblemResponses problems
    ) {
        this.accounts = accounts;
        this.limiter = limiter;
        this.problems = problems;
    }

    /** Signed in: 200 with the person, as {@code GET /api/auth/me} returns it. */
    public void onLoginSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        StaffPrincipal principal = (StaffPrincipal) authentication.getPrincipal();
        limiter.recordSuccess(principal.getUsername());
        accounts.recordLogin(principal.id());
        problems.json(response, CurrentStaff.from(principal));
    }

    /**
     * Wrong name, wrong password and deactivated account all get the same
     * answer, so the response does not reveal which accounts exist.
     */
    public void onLoginFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        limiter.recordFailure(request.getParameter("username"), request.getRemoteAddr());
        problems.problem(request, response, HttpStatus.UNAUTHORIZED,
                "Anmeldung fehlgeschlagen",
                "Benutzername oder Passwort ist falsch.");
    }

    /** Not signed in (or the session ended) and the request needs it. */
    public void onNotSignedIn(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        problems.problem(request, response, HttpStatus.UNAUTHORIZED,
                "Nicht angemeldet",
                "Bitte melde dich an.");
    }

    public void onAccessDenied(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {
        if (exception instanceof CsrfException) {
            problems.problem(request, response, HttpStatus.FORBIDDEN,
                    "Sitzung abgelaufen",
                    "Deine Sitzung ist abgelaufen. Bitte lade die Seite neu.");
            return;
        }
        problems.problem(request, response, HttpStatus.FORBIDDEN,
                "Keine Berechtigung",
                "Dafür fehlt dir die Berechtigung.");
    }
}
