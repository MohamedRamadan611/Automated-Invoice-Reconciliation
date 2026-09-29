package com.agent.reconciliation.repository;

import com.agent.reconciliation.domain.entity.Invoice;
import com.agent.reconciliation.domain.entity.ReconciliationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @EntityGraph(attributePaths = {"audits"})
    Optional<Invoice> findWithAuditsById(Long id);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    List<Invoice> findByPoReference(String poReference);

    List<Invoice> findByReconciliationStatus(ReconciliationStatus status);
}
