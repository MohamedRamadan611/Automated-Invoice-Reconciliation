package com.agent.reconciliation.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable DTO representing an invoice summary item in the invoices list view.
 */
public record InvoiceListItemResponse(
        Long id,
        String invoiceNumber,
        String poReference,
        String vendorName,
        BigDecimal invoicedTotal,
        String reconciliationStatus,
        int discrepancyCount,
        Instant createdAt
) {}
