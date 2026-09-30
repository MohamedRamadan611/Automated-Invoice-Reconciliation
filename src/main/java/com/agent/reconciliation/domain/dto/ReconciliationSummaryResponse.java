package com.agent.reconciliation.domain.dto;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Immutable DTO representing the summarized status, line items, and audit findings of a reconciled invoice.
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
        List<BilledLineItemResponse> billedItems,
        String disputeDraft,
        String disputeDraftArabic,
        String disputeDraftEnglish,
        String fileDownloadUri
) {
    public ReconciliationSummaryResponse(
            Long invoiceId,
            String invoiceNumber,
            String poReference,
            String vendorName,
            String reconciliationStatus,
            BigDecimal invoicedTotal,
            BigDecimal expectedTotal,
            int discrepancyCount,
            List<AuditDetailResponse> audits,
            List<BilledLineItemResponse> billedItems,
            String disputeDraft,
            String fileDownloadUri
    ) {
        this(invoiceId, invoiceNumber, poReference, vendorName, reconciliationStatus,
                invoicedTotal, expectedTotal, discrepancyCount, audits, billedItems,
                disputeDraft, null, null, fileDownloadUri);
    }

    public ReconciliationSummaryResponse(
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
    ) {
        this(invoiceId, invoiceNumber, poReference, vendorName, reconciliationStatus,
                invoicedTotal, expectedTotal, discrepancyCount, audits, Collections.emptyList(),
                disputeDraft, null, null, fileDownloadUri);
    }
}
