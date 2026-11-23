package com.stecker.backend.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Runs the context update (DWD weather, Werder matches) once and stops.
 *
 * For Kubernetes: with several backend copies, the built-in daily update
 * would run in every copy. There it is switched off and a CronJob starts
 * the backend with the "context-update-job" profile instead, which runs
 * this exactly once (exit code 1 on failure, so the job is retried).
 */
@Component
@ConditionalOnProperty(prefix = "stecker.context", name = "run-once", havingValue = "true")
public class ContextUpdateJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContextUpdateJob.class);

    private final ContextUpdateService updateService;
    private final ApplicationContext context;

    public ContextUpdateJob(ContextUpdateService updateService, ApplicationContext context) {
        this.updateService = updateService;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            ContextUpdateService.UpdateSummary summary = updateService.update();
            log.info("Context update (job): {} weather hours {} – {}, {} Werder home matches{}",
                    summary.weatherHours(), summary.weatherFrom(), summary.weatherTo(),
                    summary.matches(),
                    summary.importedFiles() ? ", imported the context files first" : "");
        } catch (RuntimeException error) {
            log.error("Context update (job) failed: {}", error.getMessage());
            exitCode = 1;
        }
        int code = exitCode;
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
