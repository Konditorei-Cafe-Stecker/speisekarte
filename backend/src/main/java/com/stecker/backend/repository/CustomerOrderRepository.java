package com.stecker.backend.repository;

import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.model.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository
        extends JpaRepository<CustomerOrder, Long> {

    @EntityGraph(attributePaths = {"items", "handledBy"})
    Optional<CustomerOrder> findByOrderReference(UUID orderReference);

    /** Open orders, oldest first: the waiter works through them in order. */
    @EntityGraph(attributePaths = {"items"})
    List<CustomerOrder> findAllByStatusOrderByCreatedAtAsc(OrderStatus status);

    /** Orders handled since a moment, newest first. */
    @EntityGraph(attributePaths = {"items", "handledBy"})
    List<CustomerOrder> findAllByStatusInAndHandledAtGreaterThanEqualOrderByHandledAtDesc(
            Collection<OrderStatus> statuses, Instant since);

    @EntityGraph(attributePaths = {"items", "handledBy"})
    Optional<CustomerOrder> findFirstByShortCodeAndStatusOrderByCreatedAtDesc(
            String shortCode, OrderStatus status);

    boolean existsByShortCodeAndStatus(String shortCode, OrderStatus status);
}
