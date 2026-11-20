package com.stecker.backend.dto;

import com.stecker.backend.model.StaffAccount;
import com.stecker.backend.model.StaffRole;
import com.stecker.backend.security.StaffPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Requests and responses for sign-in and staff account management. */
public final class StaffDtos {

    private StaffDtos() {
    }

    /** The person who is signed in, as the staff area needs it. */
    public record CurrentStaff(
            String username,
            String displayName,
            StaffRole role,
            boolean mustChangePassword
    ) {
        public static CurrentStaff from(StaffPrincipal principal) {
            return new CurrentStaff(
                    principal.getUsername(),
                    principal.displayName(),
                    principal.role(),
                    principal.mustChangePassword());
        }
    }

    /** One account in the admin's team list. Never contains the password. */
    public record StaffAccountResponse(
            Long id,
            String username,
            String displayName,
            StaffRole role,
            boolean active,
            boolean mustChangePassword,
            Instant createdAt,
            Instant lastLoginAt
    ) {
        public static StaffAccountResponse from(StaffAccount account) {
            return new StaffAccountResponse(
                    account.getId(),
                    account.getUsername(),
                    account.getDisplayName(),
                    account.getRole(),
                    account.isActive(),
                    account.isMustChangePassword(),
                    account.getCreatedAt(),
                    account.getLastLoginAt());
        }
    }

    public record CreateStaffRequest(
            @NotBlank(message = "Bitte gib einen Benutzernamen an.")
            String username,

            @NotBlank(message = "Bitte gib einen Namen an.")
            @Size(max = 100, message = "Der Name darf höchstens 100 Zeichen lang sein.")
            String displayName,

            @NotNull(message = "Bitte wähle eine Rolle.")
            StaffRole role,

            @NotBlank(message = "Bitte gib ein vorläufiges Passwort an.")
            String temporaryPassword
    ) {
    }

    /** Only the fields that are sent are changed. */
    public record UpdateStaffRequest(
            @Size(min = 1, max = 100,
                    message = "Der Name muss 1 bis 100 Zeichen lang sein.")
            String displayName,
            StaffRole role,
            Boolean active
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Bitte gib ein vorläufiges Passwort an.")
            String temporaryPassword
    ) {
    }

    public record ChangeOwnPasswordRequest(
            @NotBlank(message = "Bitte gib dein bisheriges Passwort an.")
            String currentPassword,

            @NotBlank(message = "Bitte gib ein neues Passwort an.")
            String newPassword
    ) {
    }
}
