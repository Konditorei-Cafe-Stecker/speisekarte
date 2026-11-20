package com.stecker.backend.service;

import com.stecker.backend.dto.StaffDtos.CreateStaffRequest;
import com.stecker.backend.dto.StaffDtos.UpdateStaffRequest;
import com.stecker.backend.model.StaffAccount;
import com.stecker.backend.model.StaffRole;
import com.stecker.backend.repository.StaffAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Creates and changes staff accounts.
 *
 * Rules that keep the café from locking itself out: nobody can deactivate
 * or demote their own account, and there is always at least one active
 * admin.
 */
@Service
public class StaffAccountService {

    public static final int MIN_PASSWORD_LENGTH = 10;
    public static final int MAX_PASSWORD_LENGTH = 128;

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,50}");

    private final StaffAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public StaffAccountService(
            StaffAccountRepository repository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StaffAccount> list() {
        return repository.findAllByOrderByActiveDescDisplayNameAsc();
    }

    /** New accounts start with a temporary password the person must replace. */
    @Transactional
    public StaffAccount create(CreateStaffRequest request) {
        String username = normalizeUsername(request.username());

        if (repository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Den Benutzernamen „" + username + "“ gibt es schon.");
        }

        checkPassword(request.temporaryPassword(), username);

        return repository.save(new StaffAccount(
                username,
                request.displayName().strip(),
                passwordEncoder.encode(request.temporaryPassword()),
                request.role(),
                clock.instant()));
    }

    @Transactional
    public StaffAccount update(long id, UpdateStaffRequest request, long actingAccountId) {
        StaffAccount account = find(id);
        boolean self = account.getId() == actingAccountId;

        if (request.displayName() != null) {
            if (request.displayName().isBlank()) {
                throw badRequest("Bitte gib einen Namen an.");
            }
            account.setDisplayName(request.displayName().strip());
        }

        if (request.role() != null && request.role() != account.getRole()) {
            if (self) {
                throw conflict("Du kannst deine eigene Rolle nicht ändern.");
            }
            if (account.getRole() == StaffRole.ADMIN && account.isActive()) {
                requireAnotherActiveAdmin();
            }
            account.setRole(request.role());
        }

        if (request.active() != null && request.active() != account.isActive()) {
            if (self) {
                throw conflict("Du kannst dein eigenes Konto nicht deaktivieren.");
            }
            if (!request.active() && account.getRole() == StaffRole.ADMIN) {
                requireAnotherActiveAdmin();
            }
            account.setActive(request.active());
        }

        return account;
    }

    /** An admin sets a temporary password; the person's sessions end. */
    @Transactional
    public void resetPassword(long id, String temporaryPassword) {
        StaffAccount account = find(id);
        checkPassword(temporaryPassword, account.getUsername());
        account.changePassword(passwordEncoder.encode(temporaryPassword), true, clock.instant());
    }

    /**
     * The signed-in person chooses their own password. Returns the moment of
     * the change, from which the current session counts as started.
     */
    @Transactional
    public Instant changeOwnPassword(long id, String currentPassword, String newPassword) {
        StaffAccount account = find(id);

        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw badRequest("Das bisherige Passwort stimmt nicht.");
        }
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw badRequest("Das neue Passwort muss sich vom bisherigen unterscheiden.");
        }
        checkPassword(newPassword, account.getUsername());

        Instant now = clock.instant();
        account.changePassword(passwordEncoder.encode(newPassword), false, now);
        return now;
    }

    @Transactional
    public void recordLogin(long id) {
        repository.findById(id).ifPresent(account -> account.recordLogin(clock.instant()));
    }

    public static String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);

        if (!USERNAME.matcher(normalized).matches()) {
            throw badRequest("Der Benutzername muss 3 bis 50 Zeichen lang sein und darf nur "
                    + "Kleinbuchstaben, Ziffern, Punkt, Bindestrich und Unterstrich enthalten.");
        }
        return normalized;
    }

    /** Length matters most; no other composition rules. */
    public static void checkPassword(String password, String username) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw badRequest("Das Passwort muss mindestens "
                    + MIN_PASSWORD_LENGTH + " Zeichen lang sein.");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw badRequest("Das Passwort darf höchstens "
                    + MAX_PASSWORD_LENGTH + " Zeichen lang sein.");
        }
        if (password.isBlank() || password.strip().equalsIgnoreCase(username)) {
            throw badRequest("Bitte wähle ein Passwort, das sich vom Benutzernamen unterscheidet.");
        }
    }

    private StaffAccount find(long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Dieses Konto gibt es nicht."));
    }

    private void requireAnotherActiveAdmin() {
        if (repository.countByRoleAndActiveTrue(StaffRole.ADMIN) <= 1) {
            throw conflict("Es muss immer mindestens ein aktives Admin-Konto geben.");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
