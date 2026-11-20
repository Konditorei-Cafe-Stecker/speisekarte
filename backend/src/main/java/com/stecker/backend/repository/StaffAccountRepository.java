package com.stecker.backend.repository;

import com.stecker.backend.model.StaffAccount;
import com.stecker.backend.model.StaffRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffAccountRepository extends JpaRepository<StaffAccount, Long> {

    Optional<StaffAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRoleAndActiveTrue(StaffRole role);

    List<StaffAccount> findAllByOrderByActiveDescDisplayNameAsc();
}
