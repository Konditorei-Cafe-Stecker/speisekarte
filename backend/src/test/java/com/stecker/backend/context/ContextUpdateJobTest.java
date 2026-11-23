package com.stecker.backend.context;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The one-shot update the Kubernetes CronJob runs (without stopping the JVM here). */
class ContextUpdateJobTest {

    private final ContextUpdateService updateService = mock(ContextUpdateService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ContextUpdateJob job =
            new ContextUpdateJob(updateService, events, mock(ApplicationContext.class));

    @Test
    void updatesAndAnnouncesTheNewData() {
        when(updateService.update()).thenReturn(new ContextUpdateService.UpdateSummary(
                Instant.parse("2026-11-21T00:00:00Z"), Instant.parse("2026-11-23T05:00:00Z"),
                53, 17, false));

        assertEquals(0, job.runOnce());
        verify(events).publishEvent(new ContextUpdatedEvent("job"));
    }

    @Test
    void reportsAFailureWithExitCodeOne() {
        when(updateService.update()).thenThrow(new IllegalStateException("Bright Sky unreachable"));

        assertEquals(1, job.runOnce());
        verify(events, never()).publishEvent(any(Object.class));
    }
}
