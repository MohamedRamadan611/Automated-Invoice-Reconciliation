package com.agent.reconciliation.repository;

import com.agent.reconciliation.domain.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @EntityGraph(attributePaths = {"items"})
    Optional<PurchaseOrder> findWithItemsByPoNumber(String poNumber);

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    boolean existsByPoNumber(String poNumber);
}
