package com.stecker.backend.controller;

import com.stecker.backend.dto.StaffDtos.CreateStaffRequest;
import com.stecker.backend.dto.StaffDtos.ResetPasswordRequest;
import com.stecker.backend.dto.StaffDtos.StaffAccountResponse;
import com.stecker.backend.dto.StaffDtos.UpdateStaffRequest;
import com.stecker.backend.model.StaffAccount;
import com.stecker.backend.security.StaffPrincipal;
import com.stecker.backend.service.StaffAccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** Staff accounts, for admins ({@code /api/admin/**} is admin-only). */
@RestController
@RequestMapping("/api/admin/staff")
public class AdminStaffController {

    private final StaffAccountService accounts;

    public AdminStaffController(StaffAccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<StaffAccountResponse> list() {
        return accounts.list().stream().map(StaffAccountResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<StaffAccountResponse> create(
            @Valid @RequestBody CreateStaffRequest request
    ) {
        StaffAccount account = accounts.create(request);
        return ResponseEntity
                .created(URI.create("/api/admin/staff/" + account.getId()))
                .body(StaffAccountResponse.from(account));
    }

    @PatchMapping("/{id}")
    public StaffAccountResponse update(
            @PathVariable long id,
            @Valid @RequestBody UpdateStaffRequest request,
            @AuthenticationPrincipal StaffPrincipal admin
    ) {
        return StaffAccountResponse.from(accounts.update(id, request, admin.id()));
    }

    /** Sets a temporary password; the person must replace it when signing in. */
    @PostMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable long id,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        accounts.resetPassword(id, request.temporaryPassword());
        return ResponseEntity.noContent().build();
    }
}
