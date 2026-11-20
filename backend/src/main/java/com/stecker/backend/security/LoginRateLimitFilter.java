package com.stecker.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Refuses sign-in attempts while {@link LoginAttemptLimiter} blocks them. */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final LoginAttemptLimiter limiter;
    private final ProblemResponses problems;

    public LoginRateLimitFilter(LoginAttemptLimiter limiter, ProblemResponses problems) {
        this.limiter = limiter;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod())
                && SecurityConfig.LOGIN_URL.equals(SecurityConfig.pathOf(request)));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        if (limiter.isBlocked(request.getParameter("username"), request.getRemoteAddr())) {
            response.setHeader("Retry-After",
                    String.valueOf(LoginAttemptLimiter.WINDOW.toSeconds()));
            problems.problem(request, response, HttpStatus.TOO_MANY_REQUESTS,
                    "Zu viele Versuche",
                    "Zu viele fehlgeschlagene Anmeldungen. Bitte warte 15 Minuten "
                            + "und versuche es dann noch einmal.");
            return;
        }
        chain.doFilter(request, response);
    }
}
