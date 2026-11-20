package com.stecker.backend.security;

import com.stecker.backend.model.StaffAccount;
import com.stecker.backend.model.StaffRole;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * The signed-in staff member, kept in the session.
 *
 * {@code signedInAt} lets the {@link ActiveAccountFilter} end sessions that
 * started before the account's password was last changed.
 */
public final class StaffPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long id;
    private final String username;
    private String passwordHash;
    private final String displayName;
    private final StaffRole role;
    private final boolean active;
    private final boolean mustChangePassword;
    private final Instant signedInAt;

    private StaffPrincipal(
            long id,
            String username,
            String passwordHash,
            String displayName,
            StaffRole role,
            boolean active,
            boolean mustChangePassword,
            Instant signedInAt
    ) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.active = active;
        this.mustChangePassword = mustChangePassword;
        this.signedInAt = signedInAt;
    }

    public static StaffPrincipal of(StaffAccount account, Instant signedInAt) {
        return new StaffPrincipal(
                account.getId(),
                account.getUsername(),
                account.getPasswordHash(),
                account.getDisplayName(),
                account.getRole(),
                account.isActive(),
                account.isMustChangePassword(),
                signedInAt);
    }

    /** The same session, with name, role and flags read again from the account. */
    public StaffPrincipal refreshedFrom(StaffAccount account) {
        return new StaffPrincipal(
                id, username, null,
                account.getDisplayName(),
                account.getRole(),
                account.isActive(),
                account.isMustChangePassword(),
                signedInAt);
    }

    /** After changing one's own password, the session counts from that moment. */
    public StaffPrincipal signedInAgainAt(Instant moment, StaffAccount account) {
        return new StaffPrincipal(
                id, username, null,
                account.getDisplayName(),
                account.getRole(),
                account.isActive(),
                account.isMustChangePassword(),
                moment);
    }

    public long id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public StaffRole role() {
        return role;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public Instant signedInAt() {
        return signedInAt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return role == StaffRole.ADMIN
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_STAFF"))
                : List.of(new SimpleGrantedAuthority("ROLE_STAFF"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
