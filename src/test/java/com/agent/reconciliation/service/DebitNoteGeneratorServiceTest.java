package com.agent.reconciliation.service;

import com.agent.reconciliation.domain.entity.Invoice;
import com.agent.reconciliation.domain.entity.IssueType;
import com.agent.reconciliation.domain.entity.ReconciliationAudit;
import com.agent.reconciliation.domain.entity.ReconciliationStatus;
import com.agent.reconciliation.repository.PurchaseOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DebitNoteGeneratorServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    private DebitNoteGeneratorService debitNoteService;

    @BeforeEach
    void setUp() {
        debitNoteService = new DebitNoteGeneratorService(purchaseOrderRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("generateDebitNotePdf: Generates valid PDF binary with itemized deductions and net balance")
    void testGenerateDebitNotePdf() throws IOException {
        String jsonPayload = """
                {
                  "invoiceNumber": "INV-DEBIT-001",
                  "vendorName": "Sinai Minerals Co",
                  "grandTotal": 10000.00,
                  "items": [
                    {
                      "suggestedSku": "MIN-QUARTZ-01",
                      "vendorItemDescription": "High Grade Quartz",
                      "quantity": 20.0,
                      "unitPrice": 250.00,
                      "lineTotal": 5000.00
                    }
                  ]
                }
                """;

        Invoice invoice = Invoice.builder()
                .id(99L)
                .invoiceNumber("INV-DEBIT-001")
                .poReference("PO-2026-99")
                .vendorName("Sinai Minerals Co")
                .invoicedTotal(new BigDecimal("10000.00"))
                .reconciliationStatus(ReconciliationStatus.FLAGGED_DISCREPANCY)
                .rawJsonPayload(jsonPayload)
                .createdAt(Instant.now())
                .build();

        // Price mismatch: billed 250 vs expected 200 (delta = 50 * 20 = 1000 EGP)
        invoice.addAudit(ReconciliationAudit.builder()
                .id(1L)
                .invoice(invoice)
                .issueType(IssueType.PRICE_MISMATCH)
                .skuCode("MIN-QUARTZ-01")
                .itemDescription("High Grade Quartz")
                .expectedValue(new BigDecimal("200.00"))
                .actualValue(new BigDecimal("250.00"))
                .explanation("Price variance")
                .build());

        // Extra fee: 300 EGP
        invoice.addAudit(ReconciliationAudit.builder()
                .id(2L)
                .invoice(invoice)
                .issueType(IssueType.EXTRA_FEE)
                .skuCode("SURCHARGE")
                .itemDescription("Freight Markup")
                .expectedValue(BigDecimal.ZERO)
                .actualValue(new BigDecimal("300.00"))
                .explanation("Unapproved fee")
                .build());

        byte[] pdfBytes = debitNoteService.generateDebitNotePdf(invoice);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(100);

        // Verify PDF Magic Bytes %PDF-
        assertThat(new String(pdfBytes, 0, 5)).isEqualTo("%PDF-");

        // Parse with PDFBox Loader to verify internal document integrity
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);

            assertThat(text).contains("COMMERCIAL DEBIT NOTE");
            assertThat(text).contains("INV-DEBIT-001");
            assertThat(text).contains("PO-2026-99");
            assertThat(text).contains("Sinai Minerals Co");
            assertThat(text).contains("NET APPROVED FOR PAYMENT");
        }
    }
}
