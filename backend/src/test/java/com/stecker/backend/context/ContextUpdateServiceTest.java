package com.stecker.backend.context;

import com.stecker.backend.model.LocalEvent;
import com.stecker.backend.model.LocalEventType;
import com.stecker.backend.model.WeatherObservation;
import com.stecker.backend.repository.LocalEventRepository;
import com.stecker.backend.repository.WeatherObservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The daily update against a fake source and a fixed "now". */
@SpringBootTest
@ActiveProfiles("test")
class ContextUpdateServiceTest {

    static final Instant NOW = Instant.parse("2026-11-20T10:23:00Z");

    @TestConfiguration
    static class FakeSourceConfig {
        @Bean
        @Primary
        FakeSource fakeSource() {
            return new FakeSource();
        }

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    /** Hands out one weather hour per requested hour and a configurable match list. */
    static class FakeSource implements ContextSource {
        final List<Instant[]> weatherRequests = new ArrayList<>();
        List<HomeMatch> matches = List.of();

        @Override
        public List<WeatherObservation> weather(Instant from, Instant to) {
            weatherRequests.add(new Instant[] {from, to});
            List<WeatherObservation> hours = new ArrayList<>();
            for (Instant t = from; t.isBefore(to); t = t.plus(Duration.ofHours(1))) {
                hours.add(new WeatherObservation(t, new BigDecimal("4.5"), BigDecimal.ZERO,
                        0, 100, new BigDecimal("8.0"), "dry", "00691"));
            }
            return hours;
        }

        @Override
        public List<HomeMatch> werderHomeMatches(int season) {
            return matches;
        }
    }

    @Autowired private ContextUpdateService updateService;
    @Autowired private ContextDataImporter importer;
    @Autowired private FakeSource source;
    @Autowired private WeatherObservationRepository weatherRepository;
    @Autowired private LocalEventRepository eventRepository;

    @BeforeEach
    @AfterEach
    void clean() {
        weatherRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        source.weatherRequests.clear();
        source.matches = List.of();
    }

    @Test
    void newDatabaseGetsTheFilesFirstThenTheMissingDaysUpToTheLastFullHour() {
        source.matches = List.of(new ContextSource.HomeMatch(83300,
                Instant.parse("2026-11-22T16:30:00Z"), "Bundesliga",
                "SV Werder Bremen", "Hamburger SV", null));

        ContextUpdateService.UpdateSummary summary = updateService.update();

        assertTrue(summary.importedFiles());
        // The files end 2026-10-31 22:00 UTC; recent hours are fetched again.
        assertEquals(Instant.parse("2026-10-29T22:00:00Z"), summary.weatherFrom());
        assertEquals(Instant.parse("2026-11-20T10:00:00Z"), summary.weatherTo());
        assertEquals(Instant.parse("2026-11-20T09:00:00Z"),
                weatherRepository.findTopByOrderByObservedAtDesc().orElseThrow().getObservedAt());
        // 8,760 hours from the file (up to 31 Oct 22:00 UTC) + every hour from
        // 31 Oct 23:00 to 20 Nov 09:00 UTC: 19 days 10 hours = 467 hours.
        assertEquals(8760 + 467, weatherRepository.count());

        LocalEvent derby = eventRepository
                .findFirstByTypeAndSource(LocalEventType.FOOTBALL, "OpenLigaDB match 83300")
                .orElseThrow();
        assertEquals("SV Werder Bremen – Hamburger SV (Bundesliga)", derby.getName());
    }

    @Test
    void secondRunFetchesOnlyRecentDaysAndUpdatesMatchesInPlace() {
        source.matches = List.of(new ContextSource.HomeMatch(83300,
                Instant.parse("2026-11-22T16:30:00Z"), "Bundesliga",
                "SV Werder Bremen", "Hamburger SV", null));
        updateService.update();
        long weatherAfterFirst = weatherRepository.count();
        long eventsAfterFirst = eventRepository.count();
        source.weatherRequests.clear();

        // The derby was moved by one hour and now has a final score.
        source.matches = List.of(new ContextSource.HomeMatch(83300,
                Instant.parse("2026-11-22T17:30:00Z"), "Bundesliga",
                "SV Werder Bremen", "Hamburger SV", "2:0"));
        ContextUpdateService.UpdateSummary second = updateService.update();

        assertFalse(second.importedFiles());
        assertEquals(Instant.parse("2026-11-18T09:00:00Z"), second.weatherFrom());
        assertEquals(1, source.weatherRequests.size());
        assertEquals(weatherAfterFirst, weatherRepository.count());

        LocalEvent derby = eventRepository
                .findFirstByTypeAndSource(LocalEventType.FOOTBALL, "OpenLigaDB match 83300")
                .orElseThrow();
        assertEquals(Instant.parse("2026-11-22T17:30:00Z"), derby.getStartsAt());
        assertEquals("Final score 2:0", derby.getNote());
        // Same number of events: the derby was updated, not added again.
        assertEquals(eventsAfterFirst, eventRepository.count());
    }

    @Test
    void importingTheFilesAgainKeepsNewerDailyData() {
        updateService.update();
        long before = weatherRepository.count();

        importer.importAll();

        assertEquals(before, weatherRepository.count());
        assertEquals(Instant.parse("2026-11-20T09:00:00Z"),
                weatherRepository.findTopByOrderByObservedAtDesc().orElseThrow().getObservedAt());
    }

    @Test
    void seasonStartsInJuly() {
        assertEquals(List.of(2026), ContextUpdateService.seasonsAround(LocalDate.of(2026, 11, 20)));
        assertEquals(List.of(2025), ContextUpdateService.seasonsAround(LocalDate.of(2026, 5, 2)));
        assertEquals(List.of(2025, 2026), ContextUpdateService.seasonsAround(LocalDate.of(2026, 6, 15)));
    }
}
