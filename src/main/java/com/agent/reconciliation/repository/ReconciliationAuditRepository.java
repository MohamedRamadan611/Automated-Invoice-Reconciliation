package com.agent.reconciliation.repository;

import com.agent.reconciliation.domain.entity.IssueType;
import com.agent.reconciliation.domain.entity.ReconciliationAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReconciliationAuditRepository extends JpaRepository<ReconciliationAudit, Long> {

    List<ReconciliationAudit> findByInvoiceId(Long invoiceId);

    List<ReconciliationAudit> findByIssueType(IssueType issueType);

    List<ReconciliationAudit> findByInvoiceIdAndIssueType(Long invoiceId, IssueType issueType);
}
