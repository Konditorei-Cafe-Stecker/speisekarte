package com.stecker.backend.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Simple protection for /api/admin until a real staff login exists:
 * the request must send the secret from STECKER_ADMIN_TOKEN in the
 * X-Admin-Token header. Without a configured token, admin access is off.
 */
@Component
public class AdminTokenInterceptor implements HandlerInterceptor {

    static final String HEADER = "X-Admin-Token";

    private final byte[] token;

    public AdminTokenInterceptor(@Value("${stecker.admin.token:}") String token) {
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (token.length == 0) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Admin access is not configured (set STECKER_ADMIN_TOKEN).");
            return false;
        }
        String sent = request.getHeader(HEADER);
        if (sent == null || !MessageDigest.isEqual(
                token, sent.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Wrong or missing admin token.");
            return false;
        }
        return true;
    }
}
