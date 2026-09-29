package com.agent.reconciliation.service;

import com.agent.reconciliation.domain.dto.ExtractedInvoice;
import com.agent.reconciliation.domain.dto.ExtractedLineItem;
import com.agent.reconciliation.domain.entity.*;
import com.agent.reconciliation.repository.InvoiceRepository;
import com.agent.reconciliation.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationEngineServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private DisputeDraftingService disputeDraftingService;

    private ReconciliationEngineService reconciliationEngineService;

    private PurchaseOrder mockPo;

    @BeforeEach
    void setUp() {
        reconciliationEngineService = new ReconciliationEngineService(
                purchaseOrderRepository, invoiceRepository, disputeDraftingService
        );

        // Standard PO with 100 tomatoes @ 20.00 EGP and 50 onions @ 15.00 EGP
        mockPo = PurchaseOrder.builder()
                .id(1L)
                .poNumber("PO-2026-001")
                .vendorName("Al-Wadi Farms")
                .totalExpectedAmount(new BigDecimal("2750.00"))
                .currency("EGP")
                .status(PoStatus.OPEN)
                .build();

        PurchaseOrderItem poItem1 = PurchaseOrderItem.builder()
                .id(101L)
                .purchaseOrder(mockPo)
                .skuCode("SKU-TOMATO-RED")
                .description("Egyptian Fresh Tomatoes (kg)")
                .expectedQuantity(new BigDecimal("100.00"))
                .agreedUnitPrice(new BigDecimal("20.00"))
                .expectedLineTotal(new BigDecimal("2000.00"))
                .build();

        PurchaseOrderItem poItem2 = PurchaseOrderItem.builder()
                .id(102L)
                .purchaseOrder(mockPo)
                .skuCode("SKU-ONION-YELLOW")
                .description("Yellow Spring Onions (kg)")
                .expectedQuantity(new BigDecimal("50.00"))
                .agreedUnitPrice(new BigDecimal("15.00"))
                .expectedLineTotal(new BigDecimal("750.00"))
                .build();

        mockPo.setItems(List.of(poItem1, poItem2));
    }

    @Test
    @DisplayName("Should approve invoice when all prices and quantities match PO with zero extra fees")
    void shouldApproveCleanInvoice() {
        when(purchaseOrderRepository.findWithItemsByPoNumber("PO-2026-001")).thenReturn(Optional.of(mockPo));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExtractedInvoice cleanInvoice = new ExtractedInvoice(
                "INV-100",
                "PO-2026-001",
                "Al-Wadi Farms",
                List.of(
                        new ExtractedLineItem("Egyptian Fresh Tomatoes", new BigDecimal("100.00"), new BigDecimal("20.00"), new BigDecimal("2000.00"), "SKU-TOMATO-RED"),
                        new ExtractedLineItem("Yellow Spring Onions", new BigDecimal("50.00"), new BigDecimal("15.00"), new BigDecimal("750.00"), "SKU-ONION-YELLOW")
                ),
                BigDecimal.ZERO,
                new BigDecimal("2750.00")
        );

        Invoice result = reconciliationEngineService.reconcile(cleanInvoice, "/uploads/clean.pdf", "{}");

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.APPROVED);
        assertThat(result.getAudits()).isEmpty();
        verify(disputeDraftingService, never()).generateDisputeDraft(any(), any(), any());
    }

    @Test
    @DisplayName("Should flag FLAGGED_DISCREPANCY and generate dispute draft on price mismatch, qty mismatch, and extra fees")
    void shouldFlagDiscrepanciesOnPriceAndQuantityVariance() {
        when(purchaseOrderRepository.findWithItemsByPoNumber("PO-2026-001")).thenReturn(Optional.of(mockPo));
        when(disputeDraftingService.generateDisputeDraft(any(), any(), any())).thenReturn("Generated Dispute Letter");
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExtractedInvoice discrepantInvoice = new ExtractedInvoice(
                "INV-101",
                "PO-2026-001",
                "Al-Wadi Farms",
                List.of(
                        // Tomatoes: Price is 25.00 instead of 20.00
                        new ExtractedLineItem("طماطم فاخرة", new BigDecimal("100.00"), new BigDecimal("25.00"), new BigDecimal("2500.00"), "SKU-TOMATO-RED"),
                        // Onions: Qty is 60 instead of 50
                        new ExtractedLineItem("بصل أحمر", new BigDecimal("60.00"), new BigDecimal("15.00"), new BigDecimal("900.00"), "SKU-ONION-YELLOW")
                ),
                new BigDecimal("150.00"), // Extra freight/delivery fee
                new BigDecimal("3550.00")
        );

        Invoice result = reconciliationEngineService.reconcile(discrepantInvoice, "/uploads/discrepant.pdf", "{}");

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.FLAGGED_DISCREPANCY);
        assertThat(result.getAudits()).hasSize(3);

        // Check PRICE_MISMATCH
        ReconciliationAudit priceAudit = result.getAudits().stream()
                .filter(a -> a.getIssueType() == IssueType.PRICE_MISMATCH)
                .findFirst().orElseThrow();
        assertThat(priceAudit.getExpectedValue()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(priceAudit.getActualValue()).isEqualByComparingTo(new BigDecimal("25.00"));

        // Check QUANTITY_MISMATCH
        ReconciliationAudit qtyAudit = result.getAudits().stream()
                .filter(a -> a.getIssueType() == IssueType.QUANTITY_MISMATCH)
                .findFirst().orElseThrow();
        assertThat(qtyAudit.getExpectedValue()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(qtyAudit.getActualValue()).isEqualByComparingTo(new BigDecimal("60.00"));

        // Check EXTRA_FEE
        ReconciliationAudit extraAudit = result.getAudits().stream()
                .filter(a -> a.getIssueType() == IssueType.EXTRA_FEE)
                .findFirst().orElseThrow();
        assertThat(extraAudit.getActualValue()).isEqualByComparingTo(new BigDecimal("150.00"));

        // Verify dispute draft was generated and assigned
        assertThat(result.getDisputeDraft()).isEqualTo("Generated Dispute Letter");
        verify(disputeDraftingService).generateDisputeDraft(any(), any(), any());
    }

    @Test
    @DisplayName("Should flag UNRECOGNIZED_ITEM when an item does not match any line in the PO")
    void shouldFlagUnrecognizedItem() {
        when(purchaseOrderRepository.findWithItemsByPoNumber("PO-2026-001")).thenReturn(Optional.of(mockPo));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExtractedInvoice invoiceWithUnknown = new ExtractedInvoice(
                "INV-102",
                "PO-2026-001",
                "Al-Wadi Farms",
                List.of(
                        new ExtractedLineItem("بطيخ جيزة فرز أول (Watermelon)", new BigDecimal("10.00"), new BigDecimal("50.00"), new BigDecimal("500.00"), null)
                ),
                BigDecimal.ZERO,
                new BigDecimal("500.00")
        );

        Invoice result = reconciliationEngineService.reconcile(invoiceWithUnknown, "/uploads/unknown.pdf", "{}");

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.FLAGGED_DISCREPANCY);
        assertThat(result.getAudits()).hasSize(1);
        assertThat(result.getAudits().get(0).getIssueType()).isEqualTo(IssueType.UNRECOGNIZED_ITEM);
    }

    @Test
    @DisplayName("Should assign MANUAL_REVIEW and flag PO_NOT_FOUND when PO reference is missing or invalid")
    void shouldFlagManualReviewWhenPoNotFound() {
        when(purchaseOrderRepository.findWithItemsByPoNumber("PO-9999-NOTFOUND")).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExtractedInvoice invoiceMissingPo = new ExtractedInvoice(
                "INV-999",
                "PO-9999-NOTFOUND",
                "Unknown Supplier",
                List.of(),
                BigDecimal.ZERO,
                new BigDecimal("1000.00")
        );

        Invoice result = reconciliationEngineService.reconcile(invoiceMissingPo, "/uploads/nopo.pdf", "{}");

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.MANUAL_REVIEW);
        assertThat(result.getAudits()).hasSize(1);
        assertThat(result.getAudits().get(0).getIssueType()).isEqualTo(IssueType.PO_NOT_FOUND);
    }
}
