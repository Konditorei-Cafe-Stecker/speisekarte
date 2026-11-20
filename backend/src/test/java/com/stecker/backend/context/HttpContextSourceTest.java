package com.stecker.backend.context;

import com.stecker.backend.context.ContextSource.HomeMatch;
import com.stecker.backend.model.WeatherObservation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Parsing of Bright Sky and OpenLigaDB answers (shapes copied from real responses). */
class HttpContextSourceTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void keepsOnlyMeasuredHoursOfStationBremen() {
        String body = """
                {"weather": [
                  {"timestamp": "2026-11-19T09:00:00+00:00", "source_id": 1,
                   "temperature": 3.4, "precipitation": 0.2, "sunshine": 12.0,
                   "cloud_cover": 75, "wind_speed": 11.2, "condition": "rain"},
                  {"timestamp": "2026-11-19T10:00:00+00:00", "source_id": 1,
                   "temperature": null, "precipitation": 0.0, "sunshine": null,
                   "cloud_cover": null, "wind_speed": null, "condition": "dry"},
                  {"timestamp": "2026-11-19T11:00:00+00:00", "source_id": 2,
                   "temperature": 5.0, "precipitation": 0.0, "sunshine": 0,
                   "cloud_cover": 100, "wind_speed": 5.0, "condition": "dry"},
                  {"timestamp": "2026-11-19T12:00:00+00:00", "source_id": 1,
                   "temperature": 4.0, "precipitation": 0.0, "sunshine": 0,
                   "cloud_cover": 100, "wind_speed": 5.0, "condition": "dry"}
                ],
                "sources": [
                  {"id": 1, "dwd_station_id": "00691", "observation_type": "recent"},
                  {"id": 2, "dwd_station_id": "00691", "observation_type": "forecast"}
                ]}""";

        List<WeatherObservation> hours = HttpContextSource.parseWeather(json.readTree(body),
                Instant.parse("2026-11-19T00:00:00Z"), Instant.parse("2026-11-19T12:00:00Z"));

        // 10:00 has no temperature, 11:00 is a forecast, 12:00 is outside the range.
        assertEquals(1, hours.size());
        WeatherObservation hour = hours.get(0);
        assertEquals(Instant.parse("2026-11-19T09:00:00Z"), hour.getObservedAt());
        assertEquals(new BigDecimal("3.4"), hour.getTemperatureC());
        assertEquals(new BigDecimal("0.2"), hour.getPrecipitationMm());
        assertEquals(12, hour.getSunshineMin());
        assertEquals("rain", hour.getCondition());
        assertEquals("00691", hour.getStation());
    }

    @Test
    void readsWerderHomeMatchesWithFinalScore() {
        String body = """
                [
                  {"matchID": 83171, "matchDateTimeUTC": "2026-09-05T13:30:00Z",
                   "team1": {"teamName": "SV Werder Bremen"}, "team2": {"teamName": "RB Leipzig"},
                   "matchResults": [
                     {"resultTypeID": 1, "pointsTeam1": 1, "pointsTeam2": 0},
                     {"resultTypeID": 2, "pointsTeam1": 3, "pointsTeam2": 1}]},
                  {"matchID": 83300, "matchDateTimeUTC": "2026-11-22T16:30:00Z",
                   "team1": {"teamName": "SV Werder Bremen"}, "team2": {"teamName": "Hamburger SV"},
                   "matchResults": []},
                  {"matchID": 83180, "matchDateTimeUTC": "2026-09-12T16:30:00Z",
                   "team1": {"teamName": "1. FC Köln"}, "team2": {"teamName": "SV Werder Bremen"},
                   "matchResults": []}
                ]""";

        List<HomeMatch> matches = HttpContextSource.parseHomeMatches(json.readTree(body), "Bundesliga");

        assertEquals(2, matches.size());
        assertEquals(new HomeMatch(83171, Instant.parse("2026-09-05T13:30:00Z"), "Bundesliga",
                "SV Werder Bremen", "RB Leipzig", "3:1"), matches.get(0));
        assertEquals("Hamburger SV", matches.get(1).awayTeam());
        assertNull(matches.get(1).finalScore());
    }
}
