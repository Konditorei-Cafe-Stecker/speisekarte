package com.stecker.backend.repository;

import com.stecker.backend.model.WeatherObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface WeatherObservationRepository
        extends JpaRepository<WeatherObservation, Instant> {

    List<WeatherObservation> findAllByOrderByObservedAtAsc();

    Optional<WeatherObservation> findTopByOrderByObservedAtDesc();

    @Query("select w.observedAt from WeatherObservation w")
    Set<Instant> findAllObservedAt();
}
