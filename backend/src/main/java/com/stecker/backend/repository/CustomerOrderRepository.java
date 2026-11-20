package com.stecker.backend.repository;

import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.model.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository
        extends JpaRepository<CustomerOrder, Long> {

    Optional<CustomerOrder> findByOrderReference(UUID orderReference);

    long countByDataSource(DataSource dataSource);

    /** Deletes the order lines of all orders from one data source. */
    @Modifying
    @Query("""
            delete from OrderItem i
            where i.customerOrder.id in (
                select o.id from CustomerOrder o where o.dataSource = :source
            )""")
    int deleteItemsByDataSource(@Param("source") DataSource source);

    /** Deletes all orders from one data source. Delete their items first. */
    @Modifying
    @Query("delete from CustomerOrder o where o.dataSource = :source")
    int deleteOrdersByDataSource(@Param("source") DataSource source);
}
