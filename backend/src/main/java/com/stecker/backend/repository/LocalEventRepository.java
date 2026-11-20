package com.stecker.backend.repository;

import com.stecker.backend.model.LocalEvent;
import com.stecker.backend.model.LocalEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocalEventRepository extends JpaRepository<LocalEvent, Long> {

    List<LocalEvent> findAllByOrderByStartsAtAsc();

    Optional<LocalEvent> findFirstByTypeAndSource(LocalEventType type, String source);

    List<LocalEvent> findAllByTypeNot(LocalEventType type);
}
