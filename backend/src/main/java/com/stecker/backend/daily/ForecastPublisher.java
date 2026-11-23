package com.stecker.backend.daily;

import com.stecker.backend.model.ForecastDay;
import com.stecker.backend.repository.ForecastDayRepository;
import com.stecker.backend.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Publishes a new 7-day forecast by running
 * {@code analysis/forecast_and_offers.py --publish}, at most once a day:
 * only when there is no forecast for today or it was made on an earlier day.
 *
 * Off unless stecker.forecast.publish.python names a Python with the
 * packages from analysis/requirements.txt. The script reads the database
 * with psql and STECKER_DB_PASSWORD, which it inherits from the backend.
 * Its output goes to analysis/output/forecast-daily.log.
 */
@Component
public class ForecastPublisher {

    private static final Logger log = LoggerFactory.getLogger(ForecastPublisher.class);
    private static final long TIMEOUT_MINUTES = 15;

    private final ForecastDayRepository forecasts;
    private final Clock clock;
    private final String python;
    private final String script;
    private final String source;

    public ForecastPublisher(
            ForecastDayRepository forecasts,
            Clock clock,
            @Value("${stecker.forecast.publish.python:}") String python,
            @Value("${stecker.forecast.publish.script:../analysis/forecast_and_offers.py}") String script,
            @Value("${stecker.forecast.publish.source:SYNTHETIC}") String source
    ) {
        this.forecasts = forecasts;
        this.clock = clock;
        this.python = python;
        this.script = script;
        this.source = source;
    }

    public void publishIfOutdated() {
        if (python.isBlank()) {
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(OrderService.CAFE_ZONE));
        if (!isOutdated(forecasts.findById(today), today)) {
            log.info("Forecast for {} is from today already; not publishing again.", today);
            return;
        }

        try {
            run();
        } catch (IOException error) {
            log.warn("Could not start the forecast script: {}", error.getMessage());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    /** True without a forecast for today, or when it was made before today. */
    static boolean isOutdated(Optional<ForecastDay> forecast, LocalDate today) {
        return forecast
                .map(day -> day.getGeneratedAt().atZone(OrderService.CAFE_ZONE).toLocalDate()
                        .isBefore(today))
                .orElse(true);
    }

    private void run() throws IOException, InterruptedException {
        Path scriptPath = Path.of(script).toAbsolutePath().normalize();
        Path analysis = scriptPath.getParent();
        Path logFile = analysis.resolve("output").resolve("forecast-daily.log");
        Files.createDirectories(logFile.getParent());

        // A path like ../analysis/.venv/bin/python is relative to the backend
        // folder; a bare name like python3 is looked up on the PATH.
        String command = python.contains("/") ? Path.of(python).toAbsolutePath().toString() : python;

        log.info("Publishing a new forecast with {} (log: {})", scriptPath.getFileName(), logFile);
        Process process = new ProcessBuilder(command, scriptPath.toString(), "--publish",
                "--source", source)
                .directory(analysis.getParent().toFile())
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();

        if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            log.warn("Forecast script took longer than {} minutes and was stopped; see {}",
                    TIMEOUT_MINUTES, logFile);
        } else if (process.exitValue() != 0) {
            log.warn("Forecast script failed (exit code {}); see {}", process.exitValue(), logFile);
        } else {
            log.info("Published a new forecast for the coming week.");
        }
    }
}
