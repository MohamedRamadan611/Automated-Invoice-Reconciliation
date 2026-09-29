package com.agent.reconciliation.domain.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable DTO representing structured invoice metadata and extracted line items from Spring AI.
 */
public record ExtractedInvoice(
        String invoiceNumber,
        String poReference,
        String vendorName,
        List<ExtractedLineItem> items,
        BigDecimal extraFees,
        BigDecimal grandTotal
) {}
