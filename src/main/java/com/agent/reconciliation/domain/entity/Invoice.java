package com.agent.reconciliation.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "invoices",
    indexes = {
        @Index(name = "idx_invoice_number", columnList = "invoice_number"),
        @Index(name = "idx_po_reference", columnList = "po_reference")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_reference", length = 50)
    private String poReference;

    @Column(name = "invoice_number", nullable = false, length = 100)
    private String invoiceNumber;

    @Column(name = "vendor_name", nullable = false, length = 150)
    private String vendorName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "invoiced_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal invoicedTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 30)
    private ReconciliationStatus reconciliationStatus;

    @Lob
    @Column(name = "dispute_draft", columnDefinition = "TEXT")
    private String disputeDraft;

    @Lob
    @Column(name = "raw_json_payload", columnDefinition = "LONGTEXT")
    private String rawJsonPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReconciliationAudit> audits = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.reconciliationStatus == null) {
            this.reconciliationStatus = ReconciliationStatus.MANUAL_REVIEW;
        }
    }

    public void addAudit(ReconciliationAudit audit) {
        audits.add(audit);
        audit.setInvoice(this);
    }

    public void removeAudit(ReconciliationAudit audit) {
        audits.remove(audit);
        audit.setInvoice(null);
    }
}
