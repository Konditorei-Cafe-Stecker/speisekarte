package com.stecker.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One hour of REAL weather measured at the DWD station "Bremen" (00691).
 * Source: Deutscher Wetterdienst Open Data via the Bright Sky API.
 * Imported from context/weather_bremen_hourly.csv.
 */
@Entity
@Table(name = "weather_hourly")
public class WeatherObservation {

    /** Start of the observed hour, UTC. */
    @Id
    private Instant observedAt;

    @Column(name = "temperature_c", nullable = false, precision = 4, scale = 1)
    private BigDecimal temperatureC;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal precipitationMm;

    /** Minutes of sunshine in the hour; empty if not measured. */
    @Column
    private Integer sunshineMin;

    @Column
    private Integer cloudCoverPct;

    @Column(precision = 5, scale = 1)
    private BigDecimal windSpeedKmh;

    /** Bright Sky condition: dry, rain, snow, sleet, hail, fog, thunderstorm. */
    @Column(length = 20)
    private String condition;

    @Column(nullable = false, length = 10)
    private String station;

    protected WeatherObservation() {
    }

    public WeatherObservation(
            Instant observedAt,
            BigDecimal temperatureC,
            BigDecimal precipitationMm,
            Integer sunshineMin,
            Integer cloudCoverPct,
            BigDecimal windSpeedKmh,
            String condition,
            String station
    ) {
        this.observedAt = observedAt;
        this.temperatureC = temperatureC;
        this.precipitationMm = precipitationMm;
        this.sunshineMin = sunshineMin;
        this.cloudCoverPct = cloudCoverPct;
        this.windSpeedKmh = windSpeedKmh;
        this.condition = condition;
        this.station = station;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public BigDecimal getTemperatureC() {
        return temperatureC;
    }

    public BigDecimal getPrecipitationMm() {
        return precipitationMm;
    }

    public Integer getSunshineMin() {
        return sunshineMin;
    }

    public Integer getCloudCoverPct() {
        return cloudCoverPct;
    }

    public BigDecimal getWindSpeedKmh() {
        return windSpeedKmh;
    }

    public String getCondition() {
        return condition;
    }

    public String getStation() {
        return station;
    }
}
