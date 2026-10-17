package com.agent.reconciliation.domain.dto;

import java.math.BigDecimal;

/**
 * Response after a supplier accepts the dispute deduction in the portal.
 */
public record PortalAcceptResponse(
        boolean success,
        String message,
        Long invoiceId,
        String invoiceNumber,
        String newStatus,
        BigDecimal netPayableAmount,
        String debitNoteDownloadUrl
) {}
