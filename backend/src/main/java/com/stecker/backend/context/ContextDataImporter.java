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

/**
 * Loads the real Bremen context data (weather, football, city events)
 * from the CSV files in src/main/resources/context into the database.
 * Running it again replaces the tables with the file contents.
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
        List<WeatherObservation> weather = readWeather();
        List<LocalEvent> football = readFootball();
        List<LocalEvent> cityEvents = readCityEvents();

        weatherRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();

        weatherRepository.saveAll(weather);
        eventRepository.saveAll(football);
        eventRepository.saveAll(cityEvents);

        return new ImportSummary(weather.size(), football.size(), cityEvents.size());
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
            Instant kickoff = Instant.parse(row.get("kickoff_utc"));
            String score = row.get("final_score");
            result.add(new LocalEvent(
                    LocalEventType.FOOTBALL,
                    row.get("home_team") + " – " + row.get("away_team")
                            + " (" + row.get("competition") + ")",
                    kickoff,
                    kickoff.plus(MATCH_LENGTH),
                    row.get("venue"),
                    2.0,
                    "OpenLigaDB match " + row.get("openligadb_match_id"),
                    score.isBlank() ? null : "Final score " + score));
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
