package com.stecker.backend.service;

import com.stecker.backend.dto.StaffDtos.CreateStaffRequest;
import com.stecker.backend.model.StaffRole;
import com.stecker.backend.repository.StaffAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffBootstrapTest {

    private final StaffAccountRepository repository = mock(StaffAccountRepository.class);
    private final StaffAccountService service = mock(StaffAccountService.class);

    @Test
    void createsTheFirstAdminWhenNoAccountExists() {
        when(repository.count()).thenReturn(0L);

        new StaffBootstrap(repository, service, "Chefin", "erstes-passwort-1", "Inhaberin")
                .run(null);

        ArgumentCaptor<CreateStaffRequest> request = ArgumentCaptor.forClass(CreateStaffRequest.class);
        verify(service).create(request.capture());
        assertEquals("Chefin", request.getValue().username());
        assertEquals("Inhaberin", request.getValue().displayName());
        assertEquals(StaffRole.ADMIN, request.getValue().role());
    }

    @Test
    void doesNothingOnceAccountsExist() {
        when(repository.count()).thenReturn(3L);

        new StaffBootstrap(repository, service, "chefin", "erstes-passwort-1", "Admin").run(null);

        verify(service, never()).create(any());
    }

    @Test
    void doesNothingWithoutBothVariables() {
        when(repository.count()).thenReturn(0L);

        new StaffBootstrap(repository, service, "chefin", "", "Admin").run(null);
        new StaffBootstrap(repository, service, "", "erstes-passwort-1", "Admin").run(null);

        verify(service, never()).create(any());
    }

    @Test
    void carriesOnWhenAnotherCopyCreatedTheAdminAtTheSameTime() {
        // Empty when this copy looked, but another copy was faster.
        when(repository.count()).thenReturn(0L, 1L);
        when(service.create(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertDoesNotThrow(() ->
                new StaffBootstrap(repository, service, "chefin", "erstes-passwort-1", "Admin")
                        .run(null));
    }

    @Test
    void stillFailsWhenTheAdminCouldNotBeCreatedAtAll() {
        // Still no account afterwards: a real problem, e.g. a too short password.
        when(repository.count()).thenReturn(0L, 0L);
        when(service.create(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwort zu kurz"));

        assertThrows(ResponseStatusException.class, () ->
                new StaffBootstrap(repository, service, "chefin", "kurz", "Admin").run(null));
    }
}
