package com.agent.reconciliation.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * Restricted DTO for external suppliers accessing the self-service dispute resolution portal.
 */
public record SupplierDisputePortalResponse(
        Long invoiceId,
        String invoiceNumber,
        String poReference,
        String vendorName,
        BigDecimal invoicedTotal,
        BigDecimal poExpectedTotal,
        @JsonProperty("totalDeductionAmount") BigDecimal totalDeductionAmount,
        BigDecimal netPayableAmount,
        String reconciliationStatus,
        List<PortalDiscrepancyItem> discrepancies,
        String disputeDraftArabic,
        String disputeDraftEnglish,
        boolean isResolved,
        String debitNoteDownloadUrl
) {
    @JsonProperty("totalDeduction")
    public BigDecimal getTotalDeduction() {
        return totalDeductionAmount;
    }

    @JsonProperty("status")
    public String getStatus() {
        return reconciliationStatus;
    }
}

