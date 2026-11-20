package com.stecker.backend.context;

import com.stecker.backend.model.LocalEvent;
import com.stecker.backend.model.LocalEventType;
import com.stecker.backend.model.WeatherObservation;
import com.stecker.backend.repository.LocalEventRepository;
import com.stecker.backend.repository.WeatherObservationRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the real Bremen context data (weather, football, city events)
 * from the CSV files in src/main/resources/context into the database.
 *
 * Running it again never removes data the daily update added: weather hours
 * already stored are kept, football matches are matched on their OpenLigaDB
 * id, and only the hand-maintained city events are replaced by the file.
 */
@Service
public class ContextDataImporter {

    static final String WEATHER_FILE = "context/weather_bremen_hourly.csv";
    static final String FOOTBALL_FILE = "context/football_bremen.csv";
    static final String EVENTS_FILE = "context/events_bremen.csv";

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Duration MATCH_LENGTH = Duration.ofHours(2);

    private final WeatherObservationRepository weatherRepository;
    private final LocalEventRepository eventRepository;

    public ContextDataImporter(
            WeatherObservationRepository weatherRepository,
            LocalEventRepository eventRepository
    ) {
        this.weatherRepository = weatherRepository;
        this.eventRepository = eventRepository;
    }

    public record ImportSummary(int weatherHours, int footballMatches, int cityEvents) {
    }

    @Transactional
    public ImportSummary importAll() {
        Set<Instant> stored = weatherRepository.findAllObservedAt();
        List<WeatherObservation> newHours = readWeather().stream()
                .filter(hour -> !stored.contains(hour.getObservedAt()))
                .toList();
        weatherRepository.saveAll(newHours);

        List<LocalEvent> football = readFootball();
        for (LocalEvent match : football) {
            saveOrUpdate(eventRepository, match);
        }

        List<LocalEvent> cityEvents = readCityEvents();
        eventRepository.deleteAll(eventRepository.findAllByTypeNot(LocalEventType.FOOTBALL));
        eventRepository.saveAll(cityEvents);

        return new ImportSummary(weatherRepository.findAllObservedAt().size(),
                football.size(), cityEvents.size());
    }

    /** Matches are identified by their source ("OpenLigaDB match 12345"). */
    static void saveOrUpdate(LocalEventRepository repository, LocalEvent event) {
        repository.findFirstByTypeAndSource(event.getType(), event.getSource())
                .ifPresentOrElse(
                        existing -> existing.update(event.getName(), event.getStartsAt(),
                                event.getEndsAt(), event.getNote()),
                        () -> repository.save(event));
    }

    /** The same LocalEvent the CSV import creates, for a match from the daily update. */
    static LocalEvent footballEvent(long openLigaDbId, Instant kickoff, String competition,
                                    String homeTeam, String awayTeam, String finalScore) {
        return new LocalEvent(
                LocalEventType.FOOTBALL,
                homeTeam + " – " + awayTeam + " (" + competition + ")",
                kickoff,
                kickoff.plus(MATCH_LENGTH),
                "Weserstadion",
                2.0,
                "OpenLigaDB match " + openLigaDbId,
                finalScore == null || finalScore.isBlank() ? null : "Final score " + finalScore);
    }

    static List<WeatherObservation> readWeather() {
        List<WeatherObservation> result = new ArrayList<>();
        for (Map<String, String> row : rows(WEATHER_FILE)) {
            result.add(new WeatherObservation(
                    Instant.parse(row.get("observed_at_utc")),
                    decimal(row.get("temperature_c")),
                    decimal(row.get("precipitation_mm")),
                    integer(row.get("sunshine_min")),
                    integer(row.get("cloud_cover_pct")),
                    decimalOrNull(row.get("wind_speed_kmh")),
                    emptyToNull(row.get("condition")),
                    row.get("station")));
        }
        return result;
    }

    static List<LocalEvent> readFootball() {
        List<LocalEvent> result = new ArrayList<>();
        for (Map<String, String> row : rows(FOOTBALL_FILE)) {
            result.add(footballEvent(
                    Long.parseLong(row.get("openligadb_match_id")),
                    Instant.parse(row.get("kickoff_utc")),
                    row.get("competition"),
                    row.get("home_team"),
                    row.get("away_team"),
                    row.get("final_score")));
        }
        return result;
    }

    static List<LocalEvent> readCityEvents() {
        List<LocalEvent> result = new ArrayList<>();
        for (Map<String, String> row : rows(EVENTS_FILE)) {
            LocalDate first = LocalDate.parse(row.get("start_date"));
            LocalDate last = LocalDate.parse(row.get("end_date"));
            String hours = row.get("daily_hours");
            String note = row.get("note");
            String combinedNote = (hours.isBlank() ? "" : "Daily " + hours + ". ")
                    + note;
            result.add(new LocalEvent(
                    LocalEventType.valueOf(row.get("type")),
                    row.get("name"),
                    first.atStartOfDay(BERLIN).toInstant(),
                    last.plusDays(1).atStartOfDay(BERLIN).toInstant(),
                    row.get("location"),
                    decimalOrNull(row.get("approx_distance_km")) == null
                            ? null
                            : Double.valueOf(row.get("approx_distance_km")),
                    row.get("source_url"),
                    combinedNote.isBlank() ? null : combinedNote.trim()));
        }
        return result;
    }

    private static List<Map<String, String>> rows(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return CsvReader.read(input);
        } catch (IOException error) {
            throw new UncheckedIOException("Could not read " + path, error);
        }
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static BigDecimal decimalOrNull(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private static Integer integer(String value) {
        return value == null || value.isBlank() ? null : Integer.valueOf(value);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
