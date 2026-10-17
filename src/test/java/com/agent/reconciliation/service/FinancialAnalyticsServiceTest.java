package com.agent.reconciliation.service;

import com.agent.reconciliation.domain.dto.FinancialRoiSummaryResponse;
import com.agent.reconciliation.domain.entity.Invoice;
import com.agent.reconciliation.domain.entity.IssueType;
import com.agent.reconciliation.domain.entity.ReconciliationAudit;
import com.agent.reconciliation.domain.entity.ReconciliationStatus;
import com.agent.reconciliation.repository.InvoiceRepository;
import com.agent.reconciliation.repository.ReconciliationAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialAnalyticsServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private ReconciliationAuditRepository auditRepository;

    private FinancialAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new FinancialAnalyticsService(invoiceRepository, auditRepository, new ObjectMapper(), null, "gemini-3.8-flash", null);
    }

    @Test
    @DisplayName("calculateRoiSummary: All clean invoices yields 100% accuracy and zero cash leakage")
    void testCalculateRoiSummary_AllClean() {
        Invoice inv1 = Invoice.builder()
                .id(1L)
                .invoiceNumber("INV-001")
                .vendorName("Cairo Supplies")
                .invoicedTotal(new BigDecimal("12000.00"))
                .reconciliationStatus(ReconciliationStatus.APPROVED)
                .createdAt(Instant.now())
                .build();

        Invoice inv2 = Invoice.builder()
                .id(2L)
                .invoiceNumber("INV-002")
                .vendorName("Nile Delta Metals")
                .invoicedTotal(new BigDecimal("8000.00"))
                .reconciliationStatus(ReconciliationStatus.APPROVED)
                .createdAt(Instant.now())
                .build();

        when(invoiceRepository.findAllWithAudits()).thenReturn(List.of(inv1, inv2));

        FinancialRoiSummaryResponse response = service.getRoiSummary();

        assertThat(response).isNotNull();
        assertThat(response.totalInvoicedVolume()).isEqualByComparingTo(new BigDecimal("20000.00"));
        assertThat(response.totalCashLeakageBlocked()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.priceMismatchLeakage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.extraFeesBlocked()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.totalInvoicesAudited()).isEqualTo(2);
        assertThat(response.cleanInvoicesCount()).isEqualTo(2);
        assertThat(response.discrepancyInvoicesCount()).isEqualTo(0);
        assertThat(response.accuracyRate()).isEqualTo(100.0);
        assertThat(response.executiveBrief()).contains("100.0%");
    }

    @Test
    @DisplayName("calculateRoiSummary: Price mismatch and extra fee correctly calculate direct cash leakage")
    void testCalculateRoiSummary_WithDiscrepancies() {
        String jsonPayload = """
                {
                  "invoiceNumber": "INV-DISC-01",
                  "vendorName": "Suez Cement",
                  "grandTotal": 15000.00,
                  "extraFees": 450.00,
                  "items": [
                    {
                      "suggestedSku": "CEM-PORT-01",
                      "vendorItemDescription": "Portland Cement Grade 42.5",
                      "quantity": 10.0,
                      "unitPrice": 145.00,
                      "lineTotal": 1450.00
                    }
                  ]
                }
                """;

        Invoice inv1 = Invoice.builder()
                .id(1L)
                .invoiceNumber("INV-DISC-01")
                .vendorName("Suez Cement")
                .invoicedTotal(new BigDecimal("15000.00"))
                .reconciliationStatus(ReconciliationStatus.FLAGGED_DISCREPANCY)
                .rawJsonPayload(jsonPayload)
                .createdAt(Instant.now())
                .build();

        // Price mismatch: billed 145.00 vs expected 125.00 (delta = 20.00 * 10 units = 200.00 EGP)
        ReconciliationAudit audit1 = ReconciliationAudit.builder()
                .id(101L)
                .invoice(inv1)
                .issueType(IssueType.PRICE_MISMATCH)
                .skuCode("CEM-PORT-01")
                .itemDescription("Portland Cement Grade 42.5")
                .expectedValue(new BigDecimal("125.00"))
                .actualValue(new BigDecimal("145.00"))
                .explanation("Price mismatch of 20 EGP per unit")
                .build();
        inv1.addAudit(audit1);

        // Extra fee: 450.00 EGP unapproved surcharge
        ReconciliationAudit audit2 = ReconciliationAudit.builder()
                .id(102L)
                .invoice(inv1)
                .issueType(IssueType.EXTRA_FEE)
                .skuCode("SURCHARGE")
                .itemDescription("Freight markup")
                .expectedValue(BigDecimal.ZERO)
                .actualValue(new BigDecimal("450.00"))
                .explanation("Unapproved freight charge")
                .build();
        inv1.addAudit(audit2);

        // Clean invoice
        Invoice inv2 = Invoice.builder()
                .id(2L)
                .invoiceNumber("INV-CLEAN-02")
                .vendorName("Giza Tools")
                .invoicedTotal(new BigDecimal("5000.00"))
                .reconciliationStatus(ReconciliationStatus.APPROVED)
                .createdAt(Instant.now())
                .build();

        when(invoiceRepository.findAllWithAudits()).thenReturn(List.of(inv1, inv2));

        FinancialRoiSummaryResponse response = service.getRoiSummary();

        assertThat(response).isNotNull();
        assertThat(response.totalInvoicedVolume()).isEqualByComparingTo(new BigDecimal("20000.00"));
        // 200.00 price mismatch + 450.00 extra fee = 650.00 EGP leakage prevented
        assertThat(response.priceMismatchLeakage()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(response.extraFeesBlocked()).isEqualByComparingTo(new BigDecimal("450.00"));
        assertThat(response.totalCashLeakageBlocked()).isEqualByComparingTo(new BigDecimal("650.00"));
        assertThat(response.totalInvoicesAudited()).isEqualTo(2);
        assertThat(response.cleanInvoicesCount()).isEqualTo(1);
        assertThat(response.discrepancyInvoicesCount()).isEqualTo(1);
        assertThat(response.accuracyRate()).isEqualTo(50.0);
        assertThat(response.topOffenses()).isNotEmpty();
    }
}
