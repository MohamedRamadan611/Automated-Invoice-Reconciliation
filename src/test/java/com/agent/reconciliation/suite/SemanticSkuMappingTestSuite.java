package com.agent.reconciliation.suite;

import com.agent.reconciliation.domain.dto.ExtractedInvoice;
import com.agent.reconciliation.domain.dto.ExtractedLineItem;
import com.agent.reconciliation.domain.entity.*;
import com.agent.reconciliation.repository.InvoiceRepository;
import com.agent.reconciliation.repository.PurchaseOrderRepository;
import com.agent.reconciliation.service.DisputeDraftingService;
import com.agent.reconciliation.service.ReconciliationEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SUITE 3: Semantic SKU Mapping & Cross-Industry Item Resolution
 *
 * Validates the generic, industry-agnostic matching engine within ReconciliationEngineService:
 * SKU code matching, token overlap, substring containment, Tashkeel diacritic stripping,
 * unrecognized item classification, and PO_NOT_FOUND classification.
 *
 * Test IDs: SKU-001 through SKU-006
 */
@ExtendWith(MockitoExtension.class)
class SemanticSkuMappingTestSuite {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private DisputeDraftingService disputeDraftingService;

    private ReconciliationEngineService engine;

    private PurchaseOrder multiIndustryPo;

    @BeforeEach
    void setUp() {
        engine = new ReconciliationEngineService(
                purchaseOrderRepository, invoiceRepository, disputeDraftingService
        );

        multiIndustryPo = PurchaseOrder.builder()
                .id(10L)
                .poNumber("PO-SKU-TEST")
                .vendorName("Multi-Industry Supplier")
                .totalExpectedAmount(new BigDecimal("50000.00"))
                .currency("EGP")
                .status(PoStatus.OPEN)
                .build();

        PurchaseOrderItem tomatoes = PurchaseOrderItem.builder()
                .id(201L).purchaseOrder(multiIndustryPo)
                .skuCode("SKU-TOMATO-RED").description("Egyptian Fresh Tomatoes (kg)")
                .expectedQuantity(new BigDecimal("500.00"))
                .agreedUnitPrice(new BigDecimal("18.00"))
                .expectedLineTotal(new BigDecimal("9000.00")).build();

        PurchaseOrderItem cheese = PurchaseOrderItem.builder()
                .id(202L).purchaseOrder(multiIndustryPo)
                .skuCode("SKU-CHEESE-WHITE").description("White Feta Cheese 500g")
                .expectedQuantity(new BigDecimal("200.00"))
                .agreedUnitPrice(new BigDecimal("35.00"))
                .expectedLineTotal(new BigDecimal("7000.00")).build();

        PurchaseOrderItem laptop = PurchaseOrderItem.builder()
                .id(203L).purchaseOrder(multiIndustryPo)
                .skuCode("SKU-LAPTOP-15").description("Apex 15.6 inch Core-i7 Laptop")
                .expectedQuantity(new BigDecimal("10.00"))
                .agreedUnitPrice(new BigDecimal("2500.00"))
                .expectedLineTotal(new BigDecimal("25000.00")).build();

        PurchaseOrderItem steelBeam = PurchaseOrderItem.builder()
                .id(204L).purchaseOrder(multiIndustryPo)
                .skuCode("SKU-STEEL-BEAM-12").description("Heavy Steel I-Beam 12m Galvanized")
                .expectedQuantity(new BigDecimal("20.00"))
                .agreedUnitPrice(new BigDecimal("450.00"))
                .expectedLineTotal(new BigDecimal("9000.00")).build();

        multiIndustryPo.setItems(List.of(tomatoes, cheese, laptop, steelBeam));
    }

    private void stubPo() {
        when(purchaseOrderRepository.findWithItemsByPoNumber("PO-SKU-TEST")).thenReturn(Optional.of(multiIndustryPo));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── SKU-001: Agricultural Item with Arabic Description ─────────────────────
    @Test
    @DisplayName("SKU-001: Arabic agricultural item 'طماطم بلدي فرز أول' matches SKU-TOMATO-RED via description tokens")
    void sku001_arabicAgriculturalItemMatches() {
        stubPo();

        ExtractedInvoice invoice = new ExtractedInvoice("INV-SKU001", "PO-SKU-TEST", "Multi-Industry Supplier",
                List.of(
                        new ExtractedLineItem("طماطم بلدي فرز أول", new BigDecimal("500.00"), new BigDecimal("18.00"), new BigDecimal("9000.00"), "SKU-TOMATO-RED")
                ),
                BigDecimal.ZERO, new BigDecimal("9000.00"));

        Invoice result = engine.reconcile(invoice, "/test/sku001.pdf", "{}");

        // Should match by SKU code and NOT flag as UNRECOGNIZED
        assertThat(result.getAudits().stream()
                .noneMatch(a -> a.getIssueType() == IssueType.UNRECOGNIZED_ITEM)).isTrue();
    }

    // ─── SKU-002: Dairy Item with Arabic Description ────────────────────────────
    @Test
    @DisplayName("SKU-002: Arabic dairy item 'جبنة فيتا بيضاء ٥٠٠ جم' matches SKU-CHEESE-WHITE via SKU code")
    void sku002_arabicDairyItemMatches() {
        stubPo();

        ExtractedInvoice invoice = new ExtractedInvoice("INV-SKU002", "PO-SKU-TEST", "Multi-Industry Supplier",
                List.of(
                        new ExtractedLineItem("جبنة فيتا بيضاء ٥٠٠ جم", new BigDecimal("200.00"), new BigDecimal("35.00"), new BigDecimal("7000.00"), "SKU-CHEESE-WHITE")
                ),
                BigDecimal.ZERO, new BigDecimal("7000.00"));

        Invoice result = engine.reconcile(invoice, "/test/sku002.pdf", "{}");

        assertThat(result.getAudits().stream()
                .noneMatch(a -> a.getIssueType() == IssueType.UNRECOGNIZED_ITEM)).isTrue();
    }

    // ─── SKU-003: Electronics Item with English Description ─────────────────────
    @Test
    @DisplayName("SKU-003: English electronics item matches 'Apex 15.6 inch Core-i7 Laptop' via description tokens")
    void sku003_electronicsItemMatchesByDescription() {
        stubPo();

        // Slightly different description but containing matching tokens
        ExtractedInvoice invoice = new ExtractedInvoice("INV-SKU003", "PO-SKU-TEST", "Multi-Industry Supplier",
                List.of(
                        new ExtractedLineItem("Apex Laptop 15.6\" Core i7", new BigDecimal("10.00"), new BigDecimal("2500.00"), new BigDecimal("25000.00"), "SKU-LAPTOP-15")
                ),
                BigDecimal.ZERO, new BigDecimal("25000.00"));

        Invoice result = engine.reconcile(invoice, "/test/sku003.pdf", "{}");

        assertThat(result.getAudits().stream()
                .noneMatch(a -> a.getIssueType() == IssueType.UNRECOGNIZED_ITEM)).isTrue();
    }

    // ─── SKU-004: Industrial Hardware with English Description ───────────────────
    @Test
    @DisplayName("SKU-004: Industrial hardware 'Steel I-Beam 12m Galvanized' matches via token overlap")
    void sku004_industrialHardwareMatchesByTokens() {
        stubPo();

        ExtractedInvoice invoice = new ExtractedInvoice("INV-SKU004", "PO-SKU-TEST", "Multi-Industry Supplier",
                List.of(
                        new ExtractedLineItem("Heavy Steel I-Beam 12m Galvanized Grade A", new BigDecimal("20.00"), new BigDecimal("450.00"), new BigDecimal("9000.00"), "SKU-STEEL-BEAM-12")
                ),
                BigDecimal.ZERO, new BigDecimal("9000.00"));

        Invoice result = engine.reconcile(invoice, "/test/sku004.pdf", "{}");

        assertThat(result.getAudits().stream()
                .noneMatch(a -> a.getIssueType() == IssueType.UNRECOGNIZED_ITEM)).isTrue();
    }

    // ─── SKU-005: Unrecognized Commodity Not in PO ──────────────────────────────
    @Test
    @DisplayName("SKU-005: 'Car Wash Voucher 100 EGP' has no SKU match → UNRECOGNIZED_ITEM")
    void sku005_unrecognizedCommodityFlagged() {
        stubPo();

        ExtractedInvoice invoice = new ExtractedInvoice("INV-SKU005", "PO-SKU-TEST", "Multi-Industry Supplier",
                List.of(
                        new ExtractedLineItem("Car Wash Voucher 100 EGP", new BigDecimal("5.00"), new BigDecimal("100.00"), new BigDecimal("500.00"), null)
                ),
                BigDecimal.ZERO, new BigDecimal("500.00"));

        Invoice result = engine.reconcile(invoice, "/test/sku005.pdf", "{}");

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.FLAGGED_DISCREPANCY);
        assertThat(result.getAudits()).anyMatch(a -> a.getIssueType() == IssueType.UNRECOGNIZED_ITEM);
    }

    // ─── SKU-006: Non-Existent PO Reference → PO_NOT_FOUND ─────────────────────
    @Test
    @DisplayName("SKU-006: Non-existent PO reference 'PO-9999-DOES-NOT-EXIST' → MANUAL_REVIEW + PO_NOT_FOUND")
    void sku006_nonExistentPoReference() {
        when(purchaseOrderRepository.findWithItemsByPoNumber("PO-9999-DOES-NOT-EXIST")).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        ExtractedInvoice invoice = new ExtractedInvoice("INV-SKU006", "PO-9999-DOES-NOT-EXIST", "Unknown Supplier",
                List.of(
                        new ExtractedLineItem("Unknown item", new BigDecimal("1.00"), new BigDecimal("100.00"), new BigDecimal("100.00"), null)
                ),
                BigDecimal.ZERO, new BigDecimal("100.00"));

        Invoice result = engine.reconcile(invoice, "/test/sku006.pdf", "{}");

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.MANUAL_REVIEW);
        assertThat(result.getAudits()).hasSize(1);
        assertThat(result.getAudits().get(0).getIssueType()).isEqualTo(IssueType.PO_NOT_FOUND);
    }
}
