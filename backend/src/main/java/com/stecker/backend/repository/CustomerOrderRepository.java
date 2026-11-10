package com.stecker.backend.repository;

import com.stecker.backend.model.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository
        extends JpaRepository<CustomerOrder, Long> {

    Optional<CustomerOrder> findByOrderReference(UUID orderReference);
}
