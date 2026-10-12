package com.stecker.backend.repository;

import com.stecker.backend.model.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findAllByOrderByIdAsc();
}