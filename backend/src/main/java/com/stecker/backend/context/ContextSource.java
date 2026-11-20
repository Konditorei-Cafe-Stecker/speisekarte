package com.stecker.backend.context;

import com.stecker.backend.model.WeatherObservation;

import java.time.Instant;
import java.util.List;

/** Where the daily update gets real Bremen context data from. */
public interface ContextSource {

    /** Measured hourly weather at DWD station Bremen, from ≤ t < to. No forecasts. */
    List<WeatherObservation> weather(Instant from, Instant to);

    /** Werder Bremen home matches of one season (e.g. 2026 = 2026/27), all competitions. */
    List<HomeMatch> werderHomeMatches(int season);

    /** One Werder Bremen home match in the Weserstadion. */
    record HomeMatch(
            long openLigaDbId,
            Instant kickoff,
            String competition,
            String homeTeam,
            String awayTeam,
            String finalScore
    ) {
    }
}
