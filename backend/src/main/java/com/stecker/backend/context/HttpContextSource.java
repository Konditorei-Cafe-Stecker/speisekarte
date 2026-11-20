package com.stecker.backend.context;

import com.stecker.backend.model.WeatherObservation;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real data over the internet, no API keys needed:
 * weather from Bright Sky (Deutscher Wetterdienst open data),
 * matches from OpenLigaDB.
 */
@Component
public class HttpContextSource implements ContextSource {

    static final String STATION = "00691";
    private static final String USER_AGENT = "speisekarte-context-update/1.0";
    private static final Map<String, String> LEAGUES = Map.of(
            "bl1", "Bundesliga",
            "dfb", "DFB-Pokal");

    private final JsonMapper jsonMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public HttpContextSource(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public List<WeatherObservation> weather(Instant from, Instant to) {
        JsonNode body = get("https://api.brightsky.dev/weather?dwd_station_id=" + STATION
                + "&date=" + from + "&last_date=" + to + "&tz=Etc/UTC");
        return parseWeather(body, from, to);
    }

    @Override
    public List<HomeMatch> werderHomeMatches(int season) {
        List<HomeMatch> matches = new ArrayList<>();
        for (Map.Entry<String, String> league : LEAGUES.entrySet()) {
            JsonNode body = get("https://api.openligadb.de/getmatchdata/"
                    + league.getKey() + "/" + season);
            matches.addAll(parseHomeMatches(body, league.getValue()));
        }
        return matches;
    }

    /** Keeps measured hours of station Bremen only (forecast sources are skipped). */
    static List<WeatherObservation> parseWeather(JsonNode body, Instant from, Instant to) {
        Map<Long, JsonNode> sources = new HashMap<>();
        for (JsonNode source : body.path("sources")) {
            sources.put(source.path("id").asLong(), source);
        }

        List<WeatherObservation> result = new ArrayList<>();
        for (JsonNode hour : body.path("weather")) {
            JsonNode source = sources.get(hour.path("source_id").asLong());
            if (source == null
                    || "forecast".equals(source.path("observation_type").asString())
                    || !STATION.equals(source.path("dwd_station_id").asString())
                    || hour.path("temperature").isNull()
                    || hour.path("precipitation").isNull()) {
                continue;
            }
            Instant observedAt = OffsetDateTime.parse(hour.path("timestamp").asString()).toInstant();
            if (observedAt.isBefore(from) || !observedAt.isBefore(to)) {
                continue;
            }
            result.add(new WeatherObservation(
                    observedAt,
                    decimal(hour.path("temperature")),
                    decimal(hour.path("precipitation")),
                    integer(hour.path("sunshine")),
                    integer(hour.path("cloud_cover")),
                    hour.path("wind_speed").isNull() ? null : decimal(hour.path("wind_speed")),
                    hour.path("condition").isNull() ? null : hour.path("condition").asString(),
                    STATION));
        }
        return result;
    }

    static List<HomeMatch> parseHomeMatches(JsonNode body, String competition) {
        List<HomeMatch> matches = new ArrayList<>();
        for (JsonNode match : body) {
            String home = match.path("team1").path("teamName").asString();
            if (!home.contains("Werder")) {
                continue;
            }
            String score = null;
            for (JsonNode result : match.path("matchResults")) {
                if (result.path("resultTypeID").asInt() == 2) {  // final score
                    score = result.path("pointsTeam1").asInt() + ":" + result.path("pointsTeam2").asInt();
                }
            }
            matches.add(new HomeMatch(
                    match.path("matchID").asLong(),
                    Instant.parse(match.path("matchDateTimeUTC").asString()),
                    competition,
                    home,
                    match.path("team2").path("teamName").asString(),
                    score));
        }
        return matches;
    }

    private JsonNode get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url);
            }
            return jsonMapper.readTree(response.body());
        } catch (IOException error) {
            throw new IllegalStateException("Could not reach " + url + ": " + error.getMessage(), error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling " + url, error);
        }
    }

    private static BigDecimal decimal(JsonNode value) {
        return BigDecimal.valueOf(value.asDouble()).setScale(1, RoundingMode.HALF_UP);
    }

    private static Integer integer(JsonNode value) {
        return value.isNull() || value.isMissingNode() ? null : (int) Math.round(value.asDouble());
    }
}
