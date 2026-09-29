package com.agent.reconciliation.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "purchase_orders",
    indexes = {
        @Index(name = "idx_po_number", columnList = "po_number", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_number", nullable = false, unique = true, length = 50)
    private String poNumber;

    @Column(name = "vendor_name", nullable = false, length = 150)
    private String vendorName;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "EGP";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PoStatus status;

    @Column(name = "total_expected_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalExpectedAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "po", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItem> items = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.currency == null || this.currency.isBlank()) {
            this.currency = "EGP";
        }
        if (this.status == null) {
            this.status = PoStatus.OPEN;
        }
    }

    public void addItem(PurchaseOrderItem item) {
        items.add(item);
        item.setPo(this);
    }

    public void removeItem(PurchaseOrderItem item) {
        items.remove(item);
        item.setPo(null);
    }
}
