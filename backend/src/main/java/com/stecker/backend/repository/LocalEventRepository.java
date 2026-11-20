package com.stecker.backend.repository;

import com.stecker.backend.model.LocalEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocalEventRepository extends JpaRepository<LocalEvent, Long> {

    List<LocalEvent> findAllByOrderByStartsAtAsc();
}
