package com.stecker.backend.repository;

import com.stecker.backend.model.ForecastDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ForecastDayRepository extends JpaRepository<ForecastDay, LocalDate> {

    List<ForecastDay> findByDayBetweenOrderByDayAsc(LocalDate from, LocalDate to);
}
