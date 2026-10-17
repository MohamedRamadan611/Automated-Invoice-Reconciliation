package com.agent.reconciliation.domain.dto;

import com.agent.reconciliation.domain.entity.IssueType;
import java.math.BigDecimal;

public record CategoryDiscrepancyStat(
        IssueType issueType,
        String label,
        long count,
        BigDecimal totalAmount
) {}
