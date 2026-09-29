package com.agent.reconciliation.domain.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable DTO representing the summarized status and audit findings of a reconciled invoice.
 */
public record ReconciliationSummaryResponse(
        Long invoiceId,
        String invoiceNumber,
        String poReference,
        String vendorName,
        String reconciliationStatus,
        BigDecimal invoicedTotal,
        BigDecimal expectedTotal,
        int discrepancyCount,
        List<AuditDetailResponse> audits,
        String disputeDraft,
        String fileDownloadUri
) {}
