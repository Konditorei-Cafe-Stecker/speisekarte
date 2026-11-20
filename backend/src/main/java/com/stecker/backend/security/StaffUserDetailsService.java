package com.stecker.backend.security;

import com.stecker.backend.repository.StaffAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;

/** Looks up the account for a sign-in attempt. */
@Service
public class StaffUserDetailsService implements UserDetailsService {

    private final StaffAccountRepository repository;
    private final Clock clock;

    public StaffUserDetailsService(StaffAccountRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String normalized = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);

        return repository.findByUsername(normalized)
                .map(account -> StaffPrincipal.of(account, clock.instant()))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown staff account"));
    }
}
