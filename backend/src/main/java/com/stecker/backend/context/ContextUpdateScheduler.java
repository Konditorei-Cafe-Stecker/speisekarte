package com.stecker.backend.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the context update every morning (default 06:15 in Bremen) and once
 * shortly after the backend starts, so days missed while it was off are
 * filled in. Failures (for example no internet) are logged, never fatal.
 * After a successful update it publishes a {@link ContextUpdatedEvent}.
 *
 * Switch off with stecker.context.daily-update.enabled=false.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "stecker.context.daily-update", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ContextUpdateScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContextUpdateScheduler.class);

    private final ContextUpdateService updateService;
    private final ApplicationEventPublisher events;

    public ContextUpdateScheduler(ContextUpdateService updateService,
                                  ApplicationEventPublisher events) {
        this.updateService = updateService;
        this.events = events;
    }

    @Scheduled(cron = "${stecker.context.daily-update.cron:0 15 6 * * *}", zone = "Europe/Berlin")
    public void everyMorning() {
        runSafely("daily");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void afterStart() {
        // In the background, so the backend answers requests right away.
        Thread.ofVirtual().name("context-update-start").start(() -> runSafely("startup"));
    }

    void runSafely(String trigger) {
        try {
            ContextUpdateService.UpdateSummary summary = updateService.update();
            log.info("Context update ({}): {} weather hours {} – {}, {} Werder home matches{}",
                    trigger, summary.weatherHours(), summary.weatherFrom(), summary.weatherTo(),
                    summary.matches(), summary.importedFiles() ? ", imported the context files first" : "");
        } catch (RuntimeException error) {
            log.warn("Context update ({}) failed, will try again next time: {}",
                    trigger, error.getMessage());
            return;
        }
        events.publishEvent(new ContextUpdatedEvent(trigger));
    }
}
