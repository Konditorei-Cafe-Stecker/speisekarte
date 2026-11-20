package com.stecker.backend.service;

import com.stecker.backend.dto.StaffDtos.CreateStaffRequest;
import com.stecker.backend.model.StaffRole;
import com.stecker.backend.repository.StaffAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the very first admin account, once.
 *
 * Only while no staff account exists at all, and only when both
 * {@code STECKER_BOOTSTRAP_ADMIN_USERNAME} and
 * {@code STECKER_BOOTSTRAP_ADMIN_PASSWORD} are set. The password counts as
 * temporary: the admin chooses a new one at the first sign-in. After that
 * the variables are ignored and can be removed.
 */
@Component
public class StaffBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffBootstrap.class);

    private final StaffAccountRepository repository;
    private final StaffAccountService service;
    private final String username;
    private final String password;
    private final String displayName;

    public StaffBootstrap(
            StaffAccountRepository repository,
            StaffAccountService service,
            @Value("${stecker.bootstrap-admin.username:}") String username,
            @Value("${stecker.bootstrap-admin.password:}") String password,
            @Value("${stecker.bootstrap-admin.display-name:Admin}") String displayName
    ) {
        this.repository = repository;
        this.service = service;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean configured = !username.isBlank() && !password.isBlank();

        if (repository.count() > 0) {
            if (configured) {
                log.info("Es gibt bereits Personal-Konten; STECKER_BOOTSTRAP_ADMIN_* "
                        + "wird ignoriert und kann entfernt werden.");
            }
            return;
        }

        if (!configured) {
            log.warn("Es gibt noch kein Personal-Konto. Setze STECKER_BOOTSTRAP_ADMIN_USERNAME "
                    + "und STECKER_BOOTSTRAP_ADMIN_PASSWORD und starte das Backend neu, "
                    + "um das erste Admin-Konto anzulegen.");
            return;
        }

        service.create(new CreateStaffRequest(username, displayName, StaffRole.ADMIN, password));
        log.info("Erstes Admin-Konto „{}“ angelegt. Beim ersten Anmelden muss ein neues "
                + "Passwort gewählt werden.", StaffAccountService.normalizeUsername(username));
    }
}
