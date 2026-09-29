package com.agent.reconciliation.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
    name = "purchase_order_items",
    indexes = {
        @Index(name = "idx_po_items_po_id", columnList = "po_id"),
        @Index(name = "idx_po_items_sku_code", columnList = "sku_code")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder po;

    @Column(name = "sku_code", nullable = false, length = 50)
    private String skuCode;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "expected_quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedQuantity;

    @Column(name = "agreed_unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal agreedUnitPrice;

    @Column(name = "expected_line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedLineTotal;
}
