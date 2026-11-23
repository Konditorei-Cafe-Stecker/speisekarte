package com.stecker.backend.daily;

import com.stecker.backend.model.ForecastDay;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastPublisherTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    @Test
    void publishesWithoutAForecastForToday() {
        assertTrue(ForecastPublisher.isOutdated(Optional.empty(), TODAY));
    }

    @Test
    void publishesWhenTodaysForecastWasMadeOnAnEarlierDay() {
        assertTrue(ForecastPublisher.isOutdated(made("2026-11-20T16:48:00Z"), TODAY));
        // 23:59 on the 22nd in Bremen.
        assertTrue(ForecastPublisher.isOutdated(made("2026-11-22T22:59:00Z"), TODAY));
    }

    @Test
    void publishesOnlyOnceADay() {
        // 00:30 on the 23rd in Bremen, although still the 22nd in UTC.
        assertFalse(ForecastPublisher.isOutdated(made("2026-11-22T23:30:00Z"), TODAY));
        assertFalse(ForecastPublisher.isOutdated(made("2026-11-23T05:20:00Z"), TODAY));
    }

    private static Optional<ForecastDay> made(String generatedAt) {
        return Optional.of(new ForecastDay(TODAY, 47, 38, 56, new BigDecimal("685.05"),
                new BigDecimal("550"), new BigDecimal("820"), -1.0, 3.0, 0, "", "",
                "SYNTHETIC", Instant.parse(generatedAt)));
    }
}
