package com.stecker.backend.repository;

import com.stecker.backend.model.ForecastHour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ForecastHourRepository extends JpaRepository<ForecastHour, Long> {

    List<ForecastHour> findByDayBetweenOrderByDayAscHourAsc(LocalDate from, LocalDate to);
}
