package com.agent.reconciliation.domain.dto;

import java.math.BigDecimal;

/**
 * Immutable DTO representing a single line item extracted from an invoice by the multimodal LLM.
 */
public record ExtractedLineItem(
        String vendorItemDescription,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String suggestedSku
) {}
