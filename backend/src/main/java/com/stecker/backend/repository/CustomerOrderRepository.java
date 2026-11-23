package com.stecker.backend.repository;

import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.model.DataSource;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.stecker.backend.model.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDateTime;
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

    long countByDataSource(DataSource dataSource);

    /** When the first order from one data source was placed (Bremen local time). */
    @Query("select min(o.createdAt) from CustomerOrder o where o.dataSource = :source")
    Optional<LocalDateTime> findFirstCreatedAt(@Param("source") DataSource source);

    /** When the latest order from one data source was placed (Bremen local time). */
    @Query("select max(o.createdAt) from CustomerOrder o where o.dataSource = :source")
    Optional<LocalDateTime> findLastCreatedAt(@Param("source") DataSource source);

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
