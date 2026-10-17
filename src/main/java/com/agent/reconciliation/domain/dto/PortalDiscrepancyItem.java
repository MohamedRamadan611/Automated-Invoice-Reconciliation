package com.agent.reconciliation.domain.dto;

import com.agent.reconciliation.domain.entity.IssueType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record PortalDiscrepancyItem(
        IssueType issueType,
        String skuCode,
        String itemDescription,
        @JsonProperty("invoicedValue") BigDecimal invoicedValue,
        @JsonProperty("agreedValue") BigDecimal agreedValue,
        @JsonProperty("deductionAmount") BigDecimal deductionAmount,
        @JsonProperty("reason") String reason
) {
    @JsonProperty("actualValue")
    public BigDecimal getActualValue() {
        return invoicedValue;
    }

    @JsonProperty("expectedValue")
    public BigDecimal getExpectedValue() {
        return agreedValue;
    }

    @JsonProperty("explanation")
    public String getExplanation() {
        return reason;
    }
}

