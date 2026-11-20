package com.stecker.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A REAL event in Bremen that may affect the café: a Werder Bremen home
 * match or a city event. Imported from context/football_bremen.csv and
 * context/events_bremen.csv; every row keeps its source.
 *
 * startsAt/endsAt are UTC. Football matches last kickoff → kickoff + 2 h.
 * Multi-day events cover the whole local days (00:00 on the first day to
 * 00:00 after the last day, Europe/Berlin).
 */
@Entity
@Table(name = "local_events")
public class LocalEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LocalEventType type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private String location;

    /** Rough straight-line distance from the café; an estimate. */
    @Column
    private Double approxDistanceKm;

    @Column(nullable = false, length = 500)
    private String source;

    @Column(length = 500)
    private String note;

    protected LocalEvent() {
    }

    public LocalEvent(
            LocalEventType type,
            String name,
            Instant startsAt,
            Instant endsAt,
            String location,
            Double approxDistanceKm,
            String source,
            String note
    ) {
        this.type = type;
        this.name = name;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.location = location;
        this.approxDistanceKm = approxDistanceKm;
        this.source = source;
        this.note = note;
    }

    /** Updates an event whose details changed at the source (rescheduled, final score). */
    public void update(String name, Instant startsAt, Instant endsAt, String note) {
        this.name = name;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.note = note;
    }

    /** True if the event is running at this moment. */
    public boolean isActiveAt(Instant moment) {
        return !moment.isBefore(startsAt) && moment.isBefore(endsAt);
    }

    public Long getId() {
        return id;
    }

    public LocalEventType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public String getLocation() {
        return location;
    }

    public Double getApproxDistanceKm() {
        return approxDistanceKm;
    }

    public String getSource() {
        return source;
    }

    public String getNote() {
        return note;
    }
}
