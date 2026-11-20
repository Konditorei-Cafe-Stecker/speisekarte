package com.stecker.backend.repository;

import com.stecker.backend.model.WeatherObservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface WeatherObservationRepository
        extends JpaRepository<WeatherObservation, Instant> {

    List<WeatherObservation> findAllByOrderByObservedAtAsc();
}
