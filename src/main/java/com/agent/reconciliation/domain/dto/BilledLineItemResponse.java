package com.agent.reconciliation.domain.dto;

import java.math.BigDecimal;

/**
 * DTO representing an individual line item extracted from the supplier's invoice document,
 * enriched with its PO reconciliation match status.
 */
public record BilledLineItemResponse(
        String description,
        String skuCode,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String matchStatus,
        String statusLabel,
        String auditExplanation
) {}
