package com.agent.reconciliation.suite;

import com.agent.reconciliation.controller.InvoiceController;
import com.agent.reconciliation.domain.dto.ReconciliationSummaryResponse;
import com.agent.reconciliation.domain.entity.Invoice;
import com.agent.reconciliation.domain.entity.ReconciliationStatus;
import com.agent.reconciliation.repository.InvoiceRepository;
import com.agent.reconciliation.repository.PurchaseOrderRepository;
import com.agent.reconciliation.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SUITE 5: Commercial Email Notification Engine & HMAC Payment Override
 *
 * Validates the email dispatch endpoints (approve/reject), HMAC override-approve endpoint,
 * manager email headers, vendor dispute dispatch, and token security contracts.
 *
 * Test IDs: MAIL-001 through MAIL-006
 */
@WebMvcTest(InvoiceController.class)
class EmailAndPaymentOverrideTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvoiceRepository invoiceRepository;

    @MockBean
    private PurchaseOrderRepository purchaseOrderRepository;

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean
    private InvoiceExtractionService extractionService;

    @MockBean
    private ReconciliationEngineService reconciliationEngineService;

    @MockBean
    private EmailNotificationService emailNotificationService;

    @MockBean
    private DisputeDraftingService disputeDraftingService;

    @MockBean
    private HmacTokenService hmacTokenService;

    private Invoice buildMockInvoice(ReconciliationStatus status) {
        return Invoice.builder()
                .id(1L)
                .invoiceNumber("INV-MAIL-TEST")
                .poReference("PO-2026-001")
                .vendorName("Test Vendor")
                .invoicedTotal(new BigDecimal("3000.00"))
                .reconciliationStatus(status)
                .createdAt(Instant.now())
                .build();
    }

    private ReconciliationSummaryResponse buildMockSummary(String status) {
        return new ReconciliationSummaryResponse(
                1L, "INV-MAIL-TEST", "PO-2026-001", "Test Vendor", status,
                new BigDecimal("3000.00"), new BigDecimal("2750.00"),
                0, Collections.emptyList(), null, "/api/invoices/1/file");
    }

    // ─── MAIL-001: Manager Approval Receipt Dispatch ────────────────────────────
    @Test
    @DisplayName("MAIL-001: POST /approve sends manager approval email and returns APPROVED status")
    void mail001_managerApprovalEmailDispatched() throws Exception {
        Invoice invoice = buildMockInvoice(ReconciliationStatus.FLAGGED_DISCREPANCY);

        when(invoiceRepository.findWithAuditsById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenReturn(invoice);
        when(reconciliationEngineService.toSummaryResponse(any())).thenReturn(buildMockSummary("APPROVED"));
        when(emailNotificationService.sendApprovalEmailToManager(any(), any(), any()))
                .thenReturn(new EmailNotificationService.EmailDispatchResult(true, true, "manager@test.com", "Simulated"));

        mockMvc.perform(post("/api/invoices/1/approve")
                        .param("email", "manager@test.com")
                        .param("notes", "Approved by finance"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Email-Dispatched", "true"))
                .andExpect(header().string("X-Email-Recipient", "manager@test.com"))
                .andExpect(jsonPath("$.reconciliationStatus").value("APPROVED"));
    }

    // ─── MAIL-002: Vendor Dispute Notification (BOTH) ───────────────────────────
    @Test
    @DisplayName("MAIL-002: POST /reject dispatches vendor dispute email with lang=BOTH")
    void mail002_vendorDisputeEmailDispatched() throws Exception {
        Invoice invoice = buildMockInvoice(ReconciliationStatus.FLAGGED_DISCREPANCY);

        when(invoiceRepository.findWithAuditsById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenReturn(invoice);
        when(reconciliationEngineService.toSummaryResponse(any())).thenReturn(buildMockSummary("REJECTED"));
        when(emailNotificationService.sendDisputeEmailToVendor(any(), any(), any()))
                .thenReturn(new EmailNotificationService.EmailDispatchResult(true, true, "vendor@test.com", "Simulated"));

        mockMvc.perform(post("/api/invoices/1/reject")
                        .param("email", "vendor@test.com")
                        .param("lang", "BOTH"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Email-Dispatched", "true"))
                .andExpect(jsonPath("$.reconciliationStatus").value("REJECTED"));
    }

    // ─── MAIL-003: Language Filtering (ARABIC) ──────────────────────────────────
    @Test
    @DisplayName("MAIL-003: POST /reject with lang=AR dispatches Arabic-only dispute")
    void mail003_arabicOnlyDisputeEmail() throws Exception {
        Invoice invoice = buildMockInvoice(ReconciliationStatus.FLAGGED_DISCREPANCY);

        when(invoiceRepository.findWithAuditsById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenReturn(invoice);
        when(reconciliationEngineService.toSummaryResponse(any())).thenReturn(buildMockSummary("REJECTED"));
        when(emailNotificationService.sendDisputeEmailToVendor(any(), any(), any()))
                .thenReturn(new EmailNotificationService.EmailDispatchResult(true, true, "vendor@test.com", "AR dispatch"));

        mockMvc.perform(post("/api/invoices/1/reject")
                        .param("email", "vendor@test.com")
                        .param("lang", "AR"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Email-Dispatched", "true"));
    }

    // ─── MAIL-004: SMTP Failure Fallback (Simulation Mode) ──────────────────────
    @Test
    @DisplayName("MAIL-004: Simulated email dispatch returns X-Email-Simulated: true")
    void mail004_smtpFailureFallback() throws Exception {
        Invoice invoice = buildMockInvoice(ReconciliationStatus.FLAGGED_DISCREPANCY);

        when(invoiceRepository.findWithAuditsById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenReturn(invoice);
        when(reconciliationEngineService.toSummaryResponse(any())).thenReturn(buildMockSummary("APPROVED"));
        when(emailNotificationService.sendApprovalEmailToManager(any(), any(), any()))
                .thenReturn(new EmailNotificationService.EmailDispatchResult(true, true, "manager@test.com", "Simulation Mode"));

        mockMvc.perform(post("/api/invoices/1/approve")
                        .param("email", "manager@test.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Email-Simulated", "true"));
    }

    // ─── MAIL-005: Valid HMAC Override Token → APPROVED ─────────────────────────
    @Test
    @DisplayName("MAIL-005: Valid HMAC token transitions invoice to APPROVED")
    void mail005_validHmacTokenApprovesInvoice() throws Exception {
        String validToken = "1:9999999999:validSignature";
        Invoice invoice = buildMockInvoice(ReconciliationStatus.FLAGGED_DISCREPANCY);

        when(hmacTokenService.verifyToken(validToken))
                .thenReturn(new HmacTokenService.TokenVerificationResult(true, false, false, 1L, "Token is valid."));
        when(invoiceRepository.findWithAuditsById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenReturn(invoice);
        when(reconciliationEngineService.toSummaryResponse(any())).thenReturn(buildMockSummary("APPROVED"));

        mockMvc.perform(get("/api/invoices/override-approve")
                        .param("token", validToken)
                        .param("notes", "Override approved by VP Finance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciliationStatus").value("APPROVED"));
    }

    // ─── MAIL-006a: Tampered HMAC Token → HTTP 403 ──────────────────────────────
    @Test
    @DisplayName("MAIL-006a: Tampered HMAC token returns HTTP 403 Forbidden")
    void mail006a_tamperedTokenReturns403() throws Exception {
        String tamperedToken = "1:9999999999:TAMPERED_SIGNATURE";

        when(hmacTokenService.verifyToken(tamperedToken))
                .thenReturn(new HmacTokenService.TokenVerificationResult(false, false, true, 1L, "Invalid signature."));

        mockMvc.perform(get("/api/invoices/override-approve")
                        .param("token", tamperedToken))
                .andExpect(status().isForbidden());
    }

    // ─── MAIL-006b: Expired HMAC Token → HTTP 400 ───────────────────────────────
    @Test
    @DisplayName("MAIL-006b: Expired HMAC token returns HTTP 400 Bad Request")
    void mail006b_expiredTokenReturns400() throws Exception {
        String expiredToken = "1:1000000000:expiredSignature";

        when(hmacTokenService.verifyToken(expiredToken))
                .thenReturn(new HmacTokenService.TokenVerificationResult(false, true, false, 1L, "Token has expired."));

        mockMvc.perform(get("/api/invoices/override-approve")
                        .param("token", expiredToken))
                .andExpect(status().isBadRequest());
    }
}
