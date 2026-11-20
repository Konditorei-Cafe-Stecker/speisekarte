package com.stecker.backend.security;

import com.stecker.backend.model.StaffAccount;
import com.stecker.backend.repository.StaffAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Checks the signed-in account on every request against the database.
 *
 * <ul>
 *   <li>Deactivated account, or password changed after the session began
 *       (an admin reset it): the session ends at once.</li>
 *   <li>Name or role changed: the session uses the new values.</li>
 *   <li>Temporary password still in use: staff and admin endpoints are
 *       refused until a new password is chosen.</li>
 * </ul>
 */
public class ActiveAccountFilter extends OncePerRequestFilter {

    /** Staff and admin endpoints; the public site stays usable regardless. */
    private static final Set<String> PROTECTED_PREFIXES = Set.of("/api/staff/", "/api/admin/");

    private final StaffAccountRepository repository;
    private final SecurityContextRepository contextRepository;
    private final ProblemResponses problems;
    private final SecurityContextHolderStrategy holder =
            SecurityContextHolder.getContextHolderStrategy();

    public ActiveAccountFilter(
            StaffAccountRepository repository,
            SecurityContextRepository contextRepository,
            ProblemResponses problems
    ) {
        this.repository = repository;
        this.contextRepository = contextRepository;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        Authentication authentication = holder.getContext().getAuthentication();

        if (authentication == null
                || !(authentication.getPrincipal() instanceof StaffPrincipal principal)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<StaffAccount> account = repository.findById(principal.id());

        if (account.isEmpty()
                || !account.get().isActive()
                || account.get().getPasswordChangedAt().isAfter(principal.signedInAt())) {
            endSession(request);
            chain.doFilter(request, response);
            return;
        }

        StaffPrincipal current = principal.refreshedFrom(account.get());
        if (current.role() != principal.role()
                || current.mustChangePassword() != principal.mustChangePassword()
                || !current.displayName().equals(principal.displayName())) {
            replace(current, request, response);
        }

        if (current.mustChangePassword() && isProtected(SecurityConfig.pathOf(request))) {
            problems.problem(request, response, HttpStatus.FORBIDDEN,
                    "Neues Passwort nötig",
                    "Bitte lege zuerst ein eigenes Passwort fest.");
            return;
        }

        chain.doFilter(request, response);
    }

    private static boolean isProtected(String path) {
        return PROTECTED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void endSession(HttpServletRequest request) {
        holder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private void replace(
            StaffPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        holder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}
