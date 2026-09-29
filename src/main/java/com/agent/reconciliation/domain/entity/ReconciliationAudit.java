package com.agent.reconciliation.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
    name = "reconciliation_audits",
    indexes = {
        @Index(name = "idx_audits_invoice_id", columnList = "invoice_id"),
        @Index(name = "idx_audits_issue_type", columnList = "issue_type")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, length = 30)
    private IssueType issueType;

    @Column(name = "sku_code", length = 50)
    private String skuCode;

    @Column(name = "item_description", nullable = false, length = 255)
    private String itemDescription;

    @Column(name = "expected_value", precision = 12, scale = 2)
    private BigDecimal expectedValue;

    @Column(name = "actual_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal actualValue;

    @Column(name = "explanation", nullable = false, length = 500)
    private String explanation;
}
