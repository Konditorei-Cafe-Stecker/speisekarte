package com.stecker.backend.security;

import com.stecker.backend.repository.StaffAccountRepository;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Who may call which endpoint.
 *
 * <ul>
 *   <li>Public: health, the menu, sending an order and checking its
 *       status, and signing in.</li>
 *   <li>{@code /api/staff/**}: any signed-in staff member.</li>
 *   <li>{@code /api/admin/**}: admins only.</li>
 *   <li>Everything else is refused, so a new endpoint is closed until it is
 *       listed here.</li>
 * </ul>
 *
 * Staff sign in with a session cookie. Every changing request from the
 * staff area must carry the CSRF token from the {@code XSRF-TOKEN} cookie in
 * the {@code X-XSRF-TOKEN} header. Sending an order is exempt: it is public,
 * does not use the session and only accepts JSON.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    public static final String LOGIN_URL = "/api/auth/login";
    public static final String LOGOUT_URL = "/api/auth/logout";

    /**
     * The request path inside the application, for example
     * {@code /api/auth/login}. (The servlet path is not reliable for this:
     * it depends on how the dispatcher servlet is mapped.)
     */
    static String pathOf(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiAuthHandlers handlers,
            LoginAttemptLimiter limiter,
            ProblemResponses problems,
            StaffAccountRepository accounts,
            SecurityContextRepository contextRepository
    ) throws Exception {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();

        http
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/menu").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/password").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/staff/**").hasAnyRole("STAFF", "ADMIN")
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf
                        .spa()
                        .ignoringRequestMatchers(paths.matcher(HttpMethod.POST, "/api/orders")))
                .securityContext(context -> context
                        .securityContextRepository(contextRepository))
                .formLogin(login -> login
                        .loginPage("/personal/login")
                        .loginProcessingUrl(LOGIN_URL)
                        .successHandler(handlers::onLoginSuccess)
                        .failureHandler(handlers::onLoginFailure)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl(LOGOUT_URL)
                        .logoutSuccessHandler(
                                new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(handlers::onNotSignedIn)
                        .accessDeniedHandler(handlers::onAccessDenied))
                .requestCache(cache -> cache.disable())
                .addFilterBefore(
                        new LoginRateLimitFilter(limiter, problems),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(
                        new ActiveAccountFilter(accounts, contextRepository, problems),
                        SecurityContextHolderFilter.class);

        return http.build();
    }
}
