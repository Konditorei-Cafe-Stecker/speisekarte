package com.stecker.backend.context;

import com.stecker.backend.model.LocalEvent;
import com.stecker.backend.model.LocalEventType;
import com.stecker.backend.model.WeatherObservation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the committed real-data files are complete and parse correctly. */
class ContextDataFilesTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void weatherCoversEveryHourOfTheYearWithoutGaps() {
        List<WeatherObservation> weather = ContextDataImporter.readWeather();

        Instant first = LocalDate.of(2025, 11, 1).atStartOfDay(BERLIN).toInstant();
        Instant last = LocalDate.of(2026, 10, 31).atTime(23, 0).atZone(BERLIN).toInstant();
        long expectedHours = Duration.between(first, last).toHours() + 1;

        long covered = weather.stream()
                .map(WeatherObservation::getObservedAt)
                .filter(t -> !t.isBefore(first) && !t.isAfter(last))
                .distinct()
                .count();

        assertEquals(expectedHours, covered);
        assertTrue(weather.stream().allMatch(w -> "00691".equals(w.getStation())));
    }

    @Test
    void footballHasRealWerderHomeMatches() {
        List<LocalEvent> matches = ContextDataImporter.readFootball();

        assertEquals(17, matches.size());
        assertTrue(matches.stream().allMatch(m -> m.getType() == LocalEventType.FOOTBALL));

        LocalEvent derby = matches.stream()
                .filter(m -> m.getName().contains("Hamburger SV"))
                .findFirst()
                .orElseThrow();
        // 18 April 2026, 15:30 in Bremen
        assertEquals(Instant.parse("2026-04-18T13:30:00Z"), derby.getStartsAt());
        assertEquals(Instant.parse("2026-04-18T15:30:00Z"), derby.getEndsAt());
    }

    @Test
    void cityEventsHaveSourcesAndLocalDates() {
        List<LocalEvent> events = ContextDataImporter.readCityEvents();

        assertEquals(21, events.size());
        assertTrue(events.stream().allMatch(e -> e.getSource().startsWith("https://")));

        // Christmas markets multiply the footfall, so their rows must not
        // overlap (the Ansgari Winterwald is listed only after 23 Dec).
        List<LocalEvent> christmasMarkets = events.stream()
                .filter(e -> e.getType() == LocalEventType.CHRISTMAS_MARKET)
                .toList();
        for (LocalEvent a : christmasMarkets) {
            for (LocalEvent b : christmasMarkets) {
                assertTrue(a == b || !a.getStartsAt().isBefore(b.getEndsAt())
                                || !b.getStartsAt().isBefore(a.getEndsAt()),
                        a.getName() + " overlaps " + b.getName());
            }
        }

        LocalEvent christmas = events.stream()
                .filter(e -> e.getType() == LocalEventType.CHRISTMAS_MARKET)
                .findFirst()
                .orElseThrow();
        assertEquals(LocalDate.of(2025, 11, 24).atStartOfDay(BERLIN).toInstant(),
                christmas.getStartsAt());
        assertEquals(LocalDate.of(2025, 12, 24).atStartOfDay(BERLIN).toInstant(),
                christmas.getEndsAt());
    }
}
