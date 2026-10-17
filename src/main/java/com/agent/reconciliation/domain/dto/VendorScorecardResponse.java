package com.agent.reconciliation.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Immutable DTO carrying vendor reliability metrics, compliance tier, and AI risk narrative.
 */
public record VendorScorecardResponse(
        String vendorName,
        int totalInvoices,
        int cleanInvoices,
        int discrepancyInvoices,
        double cleanMatchRate,
        double discrepancyRate,
        BigDecimal totalBilledAmount,
        BigDecimal totalOverchargeAttempted,
        String tier,
        List<CategoryDiscrepancyStat> topOffendingCategories,
        String riskNarrative,
        Instant lastAuditedDate
) {}
