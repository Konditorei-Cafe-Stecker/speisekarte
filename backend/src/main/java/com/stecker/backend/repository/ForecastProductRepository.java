package com.stecker.backend.repository;

import com.stecker.backend.model.ForecastProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ForecastProductRepository extends JpaRepository<ForecastProduct, Long> {

    List<ForecastProduct> findByDayBetween(LocalDate from, LocalDate to);
}
