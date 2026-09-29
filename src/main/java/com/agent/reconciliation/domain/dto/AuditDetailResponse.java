package com.agent.reconciliation.domain.dto;

import java.math.BigDecimal;

/**
 * Immutable DTO representing a specific discrepancy or audit finding flagged during reconciliation.
 */
public record AuditDetailResponse(
        Long id,
        String issueType,
        String skuCode,
        String itemDescription,
        BigDecimal expectedValue,
        BigDecimal actualValue,
        String explanation
) {}
