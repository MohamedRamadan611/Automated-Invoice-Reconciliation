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
import static org.mockito.Mockito.*;

/**
 * SUITE 1: Deterministic Financial Reconciliation & BigDecimal Arithmetic
 *
 * Validates the absolute correctness of the Java reconciliation engine's mathematical logic.
 * Every test guarantees ZERO LLM arithmetic; only pure Java BigDecimal operations.
 *
 * Test IDs: FIN-001 through FIN-008
 */
@ExtendWith(MockitoExtension.class)
class FinancialReconciliationTestSuite {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private DisputeDraftingService disputeDraftingService;

    private ReconciliationEngineService engine;

    private PurchaseOrder standardPo;

    @BeforeEach
    void setUp() {
        engine = new ReconciliationEngineService(
                purchaseOrderRepository, invoiceRepository, disputeDraftingService
        );

        // Standard PO: 100 units @ 20.00 EGP (Item A) + 50 units @ 15.00 EGP (Item B) = 2,750.00 EGP
        standardPo = PurchaseOrder.builder()
                .id(1L)
                .poNumber("PO-FIN-TEST")
                .vendorName("Financial Test Vendor")
                .totalExpectedAmount(new BigDecimal("2750.00"))
                .currency("EGP")
                .status(PoStatus.OPEN)
                .build();

        PurchaseOrderItem itemA = PurchaseOrderItem.builder()
                .id(101L)
                .purchaseOrder(standardPo)
                .skuCode("SKU-ITEM-A")
                .description("Standard Item Alpha")
                .expectedQuantity(new BigDecimal("100.00"))
                .agreedUnitPrice(new BigDecimal("20.00"))
                .expectedLineTotal(new BigDecimal("2000.00"))
                .build();

        PurchaseOrderItem itemB = PurchaseOrderItem.builder()
                .id(102L)
                .purchaseOrder(standardPo)
                .skuCode("SKU-ITEM-B")
                .description("Standard Item Beta")
                .expectedQuantity(new BigDecimal("50.00"))
                .agreedUnitPrice(new BigDecimal("15.00"))
                .expectedLineTotal(new BigDecimal("750.00"))
                .build();

        standardPo.setItems(List.of(itemA, itemB));
    }

    private void stubPo() {
        when(purchaseOrderRepository.findWithItemsByPoNumber("PO-FIN-TEST")).thenReturn(Optional.of(standardPo));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── FIN-001: Exact Match (Full Approval, Zero Audits) ──────────────────────
    @Test
    @DisplayName("FIN-001: Exact price and quantity match yields APPROVED with zero audits")
    void fin001_exactMatchYieldsApproval() {
        stubPo();

        ExtractedInvoice invoice = new ExtractedInvoice("INV-FIN001", "PO-FIN-TEST", "Financial Test Vendor",
                List.of(
                        new ExtractedLineItem("Standard Item Alpha", new BigDecimal("100.00"), new BigDecimal("20.00"), new BigDecimal("2000.00"), "SKU-ITEM-A"),
                        new ExtractedLineItem("Standard Item Beta", new BigDecimal("50.00"), new BigDecimal("15.00"), new BigDecimal("750.00"), "SKU-ITEM-B")
                ),
                BigDecimal.ZERO, new BigDecimal("2750.00"));

        Invoice result = engine.reconcile(invoice, "/test/fin001.pdf", "{}");

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.APPROVED);
        assertThat(result.getAudits()).isEmpty();
        verify(disputeDraftingService, never()).generateDisputeDraft(any(), any(), any());
    }

    // ─── FIN-002: Micro Price Variance (+0.01 EGP flags PRICE_MISMATCH) ─────────
    @Test
    @DisplayName("FIN-002: +0.01 EGP price variance flags PRICE_MISMATCH")
    void fin002_microPriceVarianceFlagsMismatch() {
        stubPo();

        ExtractedInvoice invoice = new ExtractedInvoice("INV-FIN002", "PO-FIN-TEST", "Financial Test Vendor",
                List.of(
                        // 20.01 instead of 20.00 — micro variance
                        new ExtractedLineItem("Standard Item Alpha", new BigDecimal("100.00"), new BigDecimal("20.01"), new BigDecimal("2001.00"), "SKU-ITEM-A"),
                        new ExtractedLineItem("Standard Item Beta", new BigDecimal("50.00"), new BigDecimal("15.00"), new BigDecimal("750.00"), "SKU-ITEM-B")
                ),
                BigDecimal.ZERO, new BigDecimal("2751.00"));

        Invoice result = engine.reconcile(invoice, "/test/fin002.pdf", "{}");

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.FLAGGED_DISCREPANCY);
        assertThat(result.getAudits()).anyMatch(a ->
                a.getIssueType() == IssueType.PRICE_MISMATCH
                        && a.getExpectedValue().compareTo(new BigDecimal("20.00")) == 0
                        && a.getActualValue().compareTo(new BigDecimal("20.01")) == 0);
    }

    // ─── FIN-003: Sub-cent Scale 2 BigDecimal Precision ─────────────────────────
    @Test
    @DisplayName("FIN-003: Sub-cent pricing values are correctly compared using BigDecimal scale 2")
    void fin003_subCentPrecisionHandled() {
        stubPo();

        // Price 19.995 rounds to 20.00 in scale-2 — but we're comparing raw values
        // 19.995 != 20.00, so it should flag
        ExtractedInvoice invoice = new ExtractedInvoice("INV-FIN003", "PO-FIN-TEST", "Financial Test Vendor",
                List.of(
                        new ExtractedLineItem("Standard Item Alpha", new BigDecimal("100.00"), new BigDecimal("19.995"), new BigDecimal("1999.50"), "SKU-ITEM-A"),
                        new ExtractedLineItem("Standard Item Beta", new BigDecimal("50.00"), new BigDecimal("15.00"), new BigDecimal("750.00"), "SKU-ITEM-B")
                ),
                BigDecimal.ZERO, new BigDecimal("2749.50"));

        Invoice result = engine.reconcile(invoice, "/test/fin003.pdf", "{}");

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.FLAGGED_DISCREPANCY);
        ReconciliationAudit priceAudit = result.getAudits().stream()
                .filter(a -> a.getIssueType() == IssueType.PRICE_MISMATCH)
                .findFirst().orElseThrow();
        assertThat(priceAudit.getExpectedValue()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(priceAudit.getActualValue()).isEqualByComparingTo(new BigDecimal("19.995"));
    }

    // ─── FIN-004: Quantity Over-billing (120 vs 100) ────────────────────────────
    @Test
    @DisplayName("FIN-004: Quantity over-billing (120 vs 100) flags QUANTITY_MISMATCH")
    void fin004_quantityOverBillingFlags() {
        stubPo();

        ExtractedInvoice invoice = new ExtractedInvoice("INV-FIN004", "PO-FIN-TEST", "Financial Test Vendor",
                List.of(
                        new ExtractedLineItem("Standard Item Alpha", new BigDecimal("120.00"), new BigDecimal("20.00"), new BigDecimal("2400.00"), "SKU-ITEM-A"),
                        new ExtractedLineItem("Standard Item Beta", new BigDecimal("50.00"), new BigDecimal("15.00"), new BigDecimal("750.00"), "SKU-ITEM-B")
                ),
                BigDecimal.ZERO, new BigDecimal("3150.00"));

        Invoice result = engine.reconcile(invoice, "/test/fin004.pdf", "{}");

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.FLAGGED_DISCREPANCY);
        ReconciliationAudit qtyAudit = result.getAudits().stream()
                .filter(a -> a.getIssueType() == IssueType.QUANTITY_MISMATCH)
                .findFirst().orElseThrow();
        assertThat(qtyAudit.getExpectedValue()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(qtyAudit.getActualValue()).isEqualByComparingTo(new BigDecimal("120.00"));
    }

    // ─── FIN-005: Quantity Under-billing (80 vs 100) ────────────────────────────
    @Test
    @DisplayName("FIN-005: Quantity under-billing (80 vs 100) flags QUANTITY_MISMATCH")
    void fin005_quantityUnderBillingFlags() {
        stubPo();

        ExtractedInvoice invoice = new ExtractedInvoice("INV-FIN005", "PO-FIN-TEST", "Financial Test Vendor",
                List.of(
                        new ExtractedLineItem("Standard Item Alpha", new BigDecimal("80.00"), new BigDecimal("20.00"), new BigDecimal("1600.00"), "SKU-ITEM-A"),
                        new ExtractedLineItem("Standard Item Beta", new BigDecimal("50.00"), new BigDecimal("15.00"), new BigDecimal("750.00"), "SKU-ITEM-B")
                ),
                BigDecimal.ZERO, new BigDecimal("2350.00"));

        Invoice result = engine.reconcile(invoice, "/test/fin005.pdf", "{}");

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.FLAGGED_DISCREPANCY);
        ReconciliationAudit qtyAudit = result.getAudits().stream()
                .filter(a -> a.getIssueType() == IssueType.QUANTITY_MISMATCH)
                .findFirst().orElseThrow();
        assertThat(qtyAudit.getExpectedValue()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(qtyAudit.getActualValue()).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    // ─── FIN-006: Unauthorized Delivery Fee (250 EGP EXTRA_FEE) ─────────────────
    @Test
    @DisplayName("FIN-006: Unauthorized delivery fee of 250 EGP flags EXTRA_FEE")
    void fin006_unauthorizedDeliveryFeeFlagged() {
        stubPo();

        ExtractedInvoice invoice = new ExtractedInvoice("INV-FIN006", "PO-FIN-TEST", "Financial Test Vendor",
                List.of(
                        new ExtractedLineItem("Standard Item Alpha", new BigDecimal("100.00"), new BigDecimal("20.00"), new BigDecimal("2000.00"), "SKU-ITEM-A"),
                        new ExtractedLineItem("Standard Item Beta", new BigDecimal("50.00"), new BigDecimal("15.00"), new BigDecimal("750.00"), "SKU-ITEM-B")
                ),
                new BigDecimal("250.00"), new BigDecimal("3000.00"));

        Invoice result = engine.reconcile(invoice, "/test/fin006.pdf", "{}");

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.FLAGGED_DISCREPANCY);
        ReconciliationAudit feeAudit = result.getAudits().stream()
                .filter(a -> a.getIssueType() == IssueType.EXTRA_FEE && "SURCHARGE".equals(a.getSkuCode()))
                .findFirst().orElseThrow();
        assertThat(feeAudit.getActualValue()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(feeAudit.getExpectedValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── FIN-007: Multiple Simultaneous Discrepancies (Price + Qty + Fee) ───────
    @Test
    @DisplayName("FIN-007: Multiple simultaneous discrepancies (price + qty + fee) all flagged")
    void fin007_multipleSimultaneousDiscrepancies() {
        stubPo();

        ExtractedInvoice invoice = new ExtractedInvoice("INV-FIN007", "PO-FIN-TEST", "Financial Test Vendor",
                List.of(
                        // Price mismatch: 25.00 instead of 20.00
                        new ExtractedLineItem("Standard Item Alpha", new BigDecimal("100.00"), new BigDecimal("25.00"), new BigDecimal("2500.00"), "SKU-ITEM-A"),
                        // Quantity mismatch: 60 instead of 50
                        new ExtractedLineItem("Standard Item Beta", new BigDecimal("60.00"), new BigDecimal("15.00"), new BigDecimal("900.00"), "SKU-ITEM-B")
                ),
                new BigDecimal("200.00"), // Extra fee
                new BigDecimal("3600.00"));

        Invoice result = engine.reconcile(invoice, "/test/fin007.pdf", "{}");

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.FLAGGED_DISCREPANCY);

        long priceCount = result.getAudits().stream().filter(a -> a.getIssueType() == IssueType.PRICE_MISMATCH).count();
        long qtyCount = result.getAudits().stream().filter(a -> a.getIssueType() == IssueType.QUANTITY_MISMATCH).count();
        long feeCount = result.getAudits().stream().filter(a -> a.getIssueType() == IssueType.EXTRA_FEE && "SURCHARGE".equals(a.getSkuCode())).count();

        assertThat(priceCount).isEqualTo(1);
        assertThat(qtyCount).isEqualTo(1);
        assertThat(feeCount).isEqualTo(1);
        assertThat(result.getAudits().size()).isGreaterThanOrEqualTo(3);
    }

    // ─── FIN-008: Header Grand Total vs Line Sum Mismatch ───────────────────────
    @Test
    @DisplayName("FIN-008: Header total (3500 EGP) differs from line sum (3000 EGP) — flags arithmetic discrepancy")
    void fin008_headerVsLineItemSumMismatch() {
        stubPo();

        // Lines sum: 2000 + 750 = 2750; no extra fees; but header says 3500
        ExtractedInvoice invoice = new ExtractedInvoice("INV-FIN008", "PO-FIN-TEST", "Financial Test Vendor",
                List.of(
                        new ExtractedLineItem("Standard Item Alpha", new BigDecimal("100.00"), new BigDecimal("20.00"), new BigDecimal("2000.00"), "SKU-ITEM-A"),
                        new ExtractedLineItem("Standard Item Beta", new BigDecimal("50.00"), new BigDecimal("15.00"), new BigDecimal("750.00"), "SKU-ITEM-B")
                ),
                BigDecimal.ZERO,
                new BigDecimal("3500.00")); // Header total doesn't match line sum

        Invoice result = engine.reconcile(invoice, "/test/fin008.pdf", "{}");

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.FLAGGED_DISCREPANCY);
        ReconciliationAudit sumAudit = result.getAudits().stream()
                .filter(a -> a.getIssueType() == IssueType.EXTRA_FEE && "HEADER_SUM_MISMATCH".equals(a.getSkuCode()))
                .findFirst().orElseThrow(() -> new AssertionError("FIN-008: Expected HEADER_SUM_MISMATCH audit not found"));
        assertThat(sumAudit.getExpectedValue()).isEqualByComparingTo(new BigDecimal("2750.00"));
        assertThat(sumAudit.getActualValue()).isEqualByComparingTo(new BigDecimal("3500.00"));
        assertThat(sumAudit.getExplanation()).contains("Header grand total");
    }

    // ─── FIN-008b: Header matches line sum exactly — no false positive ──────────
    @Test
    @DisplayName("FIN-008b: Header total matches line sum exactly — no false positive audit created")
    void fin008b_headerMatchesLineSumNoFalsePositive() {
        stubPo();

        // Lines: 2000 + 750 = 2750; fees: 0; header: 2750.00 — exact match
        ExtractedInvoice invoice = new ExtractedInvoice("INV-FIN008B", "PO-FIN-TEST", "Financial Test Vendor",
                List.of(
                        new ExtractedLineItem("Standard Item Alpha", new BigDecimal("100.00"), new BigDecimal("20.00"), new BigDecimal("2000.00"), "SKU-ITEM-A"),
                        new ExtractedLineItem("Standard Item Beta", new BigDecimal("50.00"), new BigDecimal("15.00"), new BigDecimal("750.00"), "SKU-ITEM-B")
                ),
                BigDecimal.ZERO, new BigDecimal("2750.00"));

        Invoice result = engine.reconcile(invoice, "/test/fin008b.pdf", "{}");

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.APPROVED);
        assertThat(result.getAudits().stream()
                .noneMatch(a -> "HEADER_SUM_MISMATCH".equals(a.getSkuCode())))
                .isTrue();
    }
}
