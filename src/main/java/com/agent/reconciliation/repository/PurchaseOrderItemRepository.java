package com.agent.reconciliation.repository;

import com.agent.reconciliation.domain.entity.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {

    List<PurchaseOrderItem> findByPoId(Long poId);

    List<PurchaseOrderItem> findByPoPoNumber(String poNumber);

    Optional<PurchaseOrderItem> findByPoIdAndSkuCode(Long poId, String skuCode);
}
