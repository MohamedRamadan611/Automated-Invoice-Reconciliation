package com.agent.reconciliation.domain.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable DTO carrying high-level portfolio ROI metrics, cash leakage prevented,
 * and executive brief for CFO reporting.
 */
public record FinancialRoiSummaryResponse(
        BigDecimal totalInvoicedVolume,
        BigDecimal totalCashLeakageBlocked,
        BigDecimal priceMismatchLeakage,
        BigDecimal extraFeesBlocked,
        BigDecimal quantityShortageValuePrevented,
        long totalInvoicesAudited,
        long cleanInvoicesCount,
        long discrepancyInvoicesCount,
        long manualReviewCount,
        double accuracyRate,
        List<CategoryDiscrepancyStat> topOffenses,
        String executiveBrief,
        String currency
) {}
