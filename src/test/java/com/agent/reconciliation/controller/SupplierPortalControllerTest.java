package com.agent.reconciliation.controller;

import com.agent.reconciliation.domain.entity.Invoice;
import com.agent.reconciliation.domain.entity.IssueType;
import com.agent.reconciliation.domain.entity.ReconciliationAudit;
import com.agent.reconciliation.domain.entity.ReconciliationStatus;
import com.agent.reconciliation.repository.InvoiceRepository;
import com.agent.reconciliation.repository.PurchaseOrderRepository;
import com.agent.reconciliation.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SupplierPortalController.class)
class SupplierPortalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvoiceRepository invoiceRepository;

    @MockBean
    private PurchaseOrderRepository purchaseOrderRepository;

    @MockBean
    private HmacTokenService hmacTokenService;

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean
    private InvoiceExtractionService extractionService;

    @MockBean
    private ReconciliationEngineService reconciliationEngineService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/portal/dispute: Valid token returns supplier dispute view")
    void testGetPortalDispute_ValidToken() throws Exception {
        String token = "42:PORTAL:9999999999:validSig";
        when(hmacTokenService.verifyPortalToken(token))
                .thenReturn(new HmacTokenService.TokenVerificationResult(true, false, false, 42L, "PORTAL", "Token is valid."));

        Invoice invoice = Invoice.builder()
                .id(42L)
                .invoiceNumber("INV-PORTAL-42")
                .poReference("PO-2026-42")
                .vendorName("Red Sea Supplies")
                .invoicedTotal(new BigDecimal("15000.00"))
                .reconciliationStatus(ReconciliationStatus.FLAGGED_DISCREPANCY)
                .createdAt(Instant.now())
                .build();

        invoice.addAudit(ReconciliationAudit.builder()
                .id(1L)
                .invoice(invoice)
                .issueType(IssueType.PRICE_MISMATCH)
                .itemDescription("Piping")
                .expectedValue(new BigDecimal("100.00"))
                .actualValue(new BigDecimal("150.00"))
                .explanation("Price variance")
                .build());

        when(invoiceRepository.findWithAuditsById(42L)).thenReturn(Optional.of(invoice));

        mockMvc.perform(get("/api/portal/dispute")
                        .param("token", token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value(42))
                .andExpect(jsonPath("$.invoiceNumber").value("INV-PORTAL-42"))
                .andExpect(jsonPath("$.vendorName").value("Red Sea Supplies"))
                .andExpect(jsonPath("$.discrepancies").isArray());
    }

    @Test
    @DisplayName("GET /api/portal/dispute: Tampered token returns HTTP 403 Forbidden")
    void testGetPortalDispute_TamperedToken() throws Exception {
        String token = "42:PORTAL:9999999999:tamperedSig";
        when(hmacTokenService.verifyPortalToken(token))
                .thenReturn(new HmacTokenService.TokenVerificationResult(false, false, true, 42L, "PORTAL", "Invalid signature."));

        mockMvc.perform(get("/api/portal/dispute")
                        .param("token", token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/portal/dispute/accept: Accepts deduction and transitions to RESOLVED_BY_DEBIT_NOTE")
    void testAcceptDeduction() throws Exception {
        String token = "42:PORTAL:9999999999:validSig";
        when(hmacTokenService.verifyPortalToken(token))
                .thenReturn(new HmacTokenService.TokenVerificationResult(true, false, false, 42L, "PORTAL", "Token is valid."));

        Invoice invoice = Invoice.builder()
                .id(42L)
                .invoiceNumber("INV-PORTAL-42")
                .poReference("PO-2026-42")
                .vendorName("Red Sea Supplies")
                .invoicedTotal(new BigDecimal("15000.00"))
                .reconciliationStatus(ReconciliationStatus.FLAGGED_DISCREPANCY)
                .createdAt(Instant.now())
                .build();

        when(invoiceRepository.findWithAuditsById(42L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/portal/dispute/accept")
                        .param("token", token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.newStatus").value("RESOLVED_BY_DEBIT_NOTE"))
                .andExpect(jsonPath("$.debitNoteDownloadUrl").value("/api/invoices/42/debit-note"));
    }

    @Test
    @DisplayName("POST /api/portal/dispute/explain: Submits counter-explanation and returns evaluation")
    void testSubmitExplanation() throws Exception {
        String token = "42:PORTAL:9999999999:validSig";
        when(hmacTokenService.verifyPortalToken(token))
                .thenReturn(new HmacTokenService.TokenVerificationResult(true, false, false, 42L, "PORTAL", "Token is valid."));

        Invoice invoice = Invoice.builder()
                .id(42L)
                .invoiceNumber("INV-PORTAL-42")
                .poReference("PO-2026-42")
                .vendorName("Red Sea Supplies")
                .invoicedTotal(new BigDecimal("15000.00"))
                .reconciliationStatus(ReconciliationStatus.FLAGGED_DISCREPANCY)
                .createdAt(Instant.now())
                .build();

        when(invoiceRepository.findWithAuditsById(42L)).thenReturn(Optional.of(invoice));

        String body = """
                {
                  "explanation": "Fuel prices spiked by 15% so emergency freight surcharge was agreed verbally."
                }
                """;

        mockMvc.perform(post("/api/portal/dispute/explain")
                        .param("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiRecommendation").isNotEmpty());
    }
}
