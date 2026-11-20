package com.stecker.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A personal login for one member of the café staff. */
@Entity
@Table(name = "staff_accounts")
public class StaffAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Lowercase, unique, used to sign in. */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** Shown in the staff area, for example "Anna". */
    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffRole role;

    @Column(nullable = false)
    private boolean active;

    /** Set while the account still has a password chosen by an admin. */
    @Column(nullable = false)
    private boolean mustChangePassword;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Sessions started before this moment are no longer valid. */
    @Column(nullable = false)
    private Instant passwordChangedAt;

    private Instant lastLoginAt;

    protected StaffAccount() {
    }

    public StaffAccount(
            String username,
            String displayName,
            String passwordHash,
            StaffRole role,
            Instant now
    ) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = true;
        this.mustChangePassword = true;
        this.createdAt = now;
        this.passwordChangedAt = now;
    }

    /**
     * Stores a new password. {@code temporary} is true when an admin set it,
     * so the person has to choose their own at the next sign-in. Every
     * session started before {@code now} ends.
     */
    public void changePassword(String newHash, boolean temporary, Instant now) {
        this.passwordHash = newHash;
        this.mustChangePassword = temporary;
        this.passwordChangedAt = now;
    }

    public void recordLogin(Instant now) {
        this.lastLoginAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public StaffRole getRole() {
        return role;
    }

    public void setRole(StaffRole role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
