package com.stecker.backend.context;

import com.stecker.backend.context.ContextSource.HomeMatch;
import com.stecker.backend.model.WeatherObservation;
import com.stecker.backend.repository.LocalEventRepository;
import com.stecker.backend.repository.WeatherObservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds the newest real context every day: measured Bremen weather up to the
 * last full hour, and new or changed Werder Bremen home matches.
 *
 * Safe to run any number of times: stored hours are overwritten with the
 * newest values (DWD corrects recent hours) and matches are updated in place.
 */
@Service
public class ContextUpdateService {

    /** Recent hours are fetched again, because DWD may still correct them. */
    static final Duration REFRESH_RECENT = Duration.ofDays(2);
    /** Upper limit when catching up after a long pause. */
    static final Duration MAX_CATCH_UP = Duration.ofDays(400);
    private static final Duration CHUNK = Duration.ofDays(30);
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private final ContextSource source;
    private final ContextDataImporter importer;
    private final WeatherObservationRepository weatherRepository;
    private final LocalEventRepository eventRepository;
    private final Clock clock;

    public ContextUpdateService(
            ContextSource source,
            ContextDataImporter importer,
            WeatherObservationRepository weatherRepository,
            LocalEventRepository eventRepository,
            Clock clock
    ) {
        this.source = source;
        this.importer = importer;
        this.weatherRepository = weatherRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    public record UpdateSummary(Instant weatherFrom, Instant weatherTo, int weatherHours,
                                int matches, boolean importedFiles) {
    }

    @Transactional
    public UpdateSummary update() {
        // A new database first gets the committed year of context data.
        boolean importedFiles = false;
        if (weatherRepository.count() == 0) {
            importer.importAll();
            importedFiles = true;
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.HOURS);
        Instant earliest = now.minus(MAX_CATCH_UP);
        Instant from = weatherRepository.findTopByOrderByObservedAtDesc()
                .map(latest -> latest.getObservedAt().minus(REFRESH_RECENT))
                .filter(start -> start.isAfter(earliest))
                .orElse(earliest);

        List<WeatherObservation> hours = new ArrayList<>();
        for (Instant start = from; start.isBefore(now); start = start.plus(CHUNK)) {
            Instant end = start.plus(CHUNK).isBefore(now) ? start.plus(CHUNK) : now;
            hours.addAll(source.weather(start, end));
        }
        weatherRepository.saveAll(hours);

        int matches = 0;
        for (int season : seasonsAround(LocalDate.now(clock.withZone(BERLIN)))) {
            for (HomeMatch match : source.werderHomeMatches(season)) {
                ContextDataImporter.saveOrUpdate(eventRepository, ContextDataImporter.footballEvent(
                        match.openLigaDbId(), match.kickoff(), match.competition(),
                        match.homeTeam(), match.awayTeam(), match.finalScore()));
                matches++;
            }
        }

        return new UpdateSummary(from, now, hours.size(), matches, importedFiles);
    }

    /** The running season (it starts in July), plus the next one from June on. */
    static List<Integer> seasonsAround(LocalDate today) {
        int season = today.getMonthValue() >= 7 ? today.getYear() : today.getYear() - 1;
        return today.getMonthValue() == 6 ? List.of(season, season + 1) : List.of(season);
    }
}
