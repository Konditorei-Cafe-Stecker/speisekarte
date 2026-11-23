package com.stecker.backend.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Runs the context update (DWD weather, Werder matches) once and stops.
 *
 * For Kubernetes: with several backend copies, the built-in daily update
 * would run in every copy. There it is switched off and a CronJob starts
 * the backend with the "context-update-job" profile instead, which runs
 * this exactly once (exit code 1 on failure, so the job is retried).
 * Like the built-in update it then publishes a {@link ContextUpdatedEvent},
 * so the follow-up work (see DailyDataUpdate) runs here as well.
 */
@Component
@ConditionalOnProperty(prefix = "stecker.context", name = "run-once", havingValue = "true")
public class ContextUpdateJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContextUpdateJob.class);

    private final ContextUpdateService updateService;
    private final ApplicationEventPublisher events;
    private final ApplicationContext context;

    public ContextUpdateJob(ContextUpdateService updateService,
                            ApplicationEventPublisher events,
                            ApplicationContext context) {
        this.updateService = updateService;
        this.events = events;
        this.context = context;
    }

    /** Updates once, then stops the app with the exit code of {@link #runOnce()}. */
    @Override
    public void run(ApplicationArguments args) {
        int code = runOnce();
        System.exit(SpringApplication.exit(context, () -> code));
    }

    /** The update itself: 0 when it worked, 1 when it failed (logged with its cause). */
    int runOnce() {
        try {
            ContextUpdateService.UpdateSummary summary = updateService.update();
            log.info("Context update (job): {} weather hours {} – {}, {} Werder home matches{}",
                    summary.weatherHours(), summary.weatherFrom(), summary.weatherTo(),
                    summary.matches(),
                    summary.importedFiles() ? ", imported the context files first" : "");
        } catch (RuntimeException error) {
            log.error("Context update (job) failed", error);
            return 1;
        }
        events.publishEvent(new ContextUpdatedEvent("job"));
        return 0;
    }
}
