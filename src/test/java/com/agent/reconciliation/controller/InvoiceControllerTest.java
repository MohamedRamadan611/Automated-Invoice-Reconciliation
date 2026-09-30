package com.agent.reconciliation.controller;

import com.agent.reconciliation.domain.dto.ExtractedInvoice;
import com.agent.reconciliation.domain.dto.ExtractedLineItem;
import com.agent.reconciliation.domain.dto.ReconciliationSummaryResponse;
import com.agent.reconciliation.domain.entity.Invoice;
import com.agent.reconciliation.domain.entity.ReconciliationStatus;
import com.agent.reconciliation.repository.InvoiceRepository;
import com.agent.reconciliation.service.FileStorageService;
import com.agent.reconciliation.service.InvoiceExtractionService;
import com.agent.reconciliation.service.ReconciliationEngineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InvoiceController.class)
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvoiceRepository invoiceRepository;

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean
    private InvoiceExtractionService extractionService;

    @MockBean
    private ReconciliationEngineService reconciliationEngineService;

    @MockBean
    private com.agent.reconciliation.service.EmailNotificationService emailNotificationService;

    @MockBean
    private com.agent.reconciliation.service.DisputeDraftingService disputeDraftingService;

    @MockBean
    private com.agent.reconciliation.repository.PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/invoices/upload should process multipart file and return 201 Created")
    void shouldUploadAndReconcileInvoice() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invoice.pdf",
                "application/pdf",
                "sample pdf data".getBytes()
        );

        ExtractedInvoice mockExtracted = new ExtractedInvoice(
                "INV-101",
                "PO-2026-001",
                "Al-Wadi Farms",
                List.of(new ExtractedLineItem("Tomatoes", new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("2000"), "SKU-TOMATO-RED")),
                BigDecimal.ZERO,
                new BigDecimal("2000")
        );

        Invoice mockInvoice = Invoice.builder()
                .id(1L)
                .invoiceNumber("INV-101")
                .poReference("PO-2026-001")
                .vendorName("Al-Wadi Farms")
                .filePath("/uploads/invoice.pdf")
                .invoicedTotal(new BigDecimal("2000.00"))
                .reconciliationStatus(ReconciliationStatus.APPROVED)
                .createdAt(Instant.now())
                .build();

        ReconciliationSummaryResponse mockSummary = new ReconciliationSummaryResponse(
                1L, "INV-101", "PO-2026-001", "Al-Wadi Farms",
                "APPROVED", new BigDecimal("2000.00"), new BigDecimal("2000.00"),
                0, Collections.emptyList(), null, "/api/invoices/1/file"
        );

        when(fileStorageService.storeFile(any())).thenReturn("/uploads/invoice.pdf");
        when(extractionService.extractInvoice(any())).thenReturn(mockExtracted);
        when(reconciliationEngineService.reconcile(any(), eq("/uploads/invoice.pdf"), any())).thenReturn(mockInvoice);
        when(reconciliationEngineService.toSummaryResponse(mockInvoice)).thenReturn(mockSummary);

        mockMvc.perform(multipart("/api/invoices/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceId").value(1))
                .andExpect(jsonPath("$.invoiceNumber").value("INV-101"))
                .andExpect(jsonPath("$.reconciliationStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /api/invoices should return list of invoices")
    void shouldReturnInvoiceList() throws Exception {
        Invoice invoice1 = Invoice.builder()
                .id(1L)
                .invoiceNumber("INV-101")
                .poReference("PO-2026-001")
                .vendorName("Al-Wadi Farms")
                .invoicedTotal(new BigDecimal("2000.00"))
                .reconciliationStatus(ReconciliationStatus.APPROVED)
                .createdAt(Instant.now())
                .build();

        when(invoiceRepository.findAllWithAudits()).thenReturn(List.of(invoice1));

        mockMvc.perform(get("/api/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].invoiceNumber").value("INV-101"))
                .andExpect(jsonPath("$[0].reconciliationStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /api/invoices/{id} should return invoice details with audits")
    void shouldReturnInvoiceById() throws Exception {
        Invoice invoice1 = Invoice.builder()
                .id(1L)
                .invoiceNumber("INV-101")
                .poReference("PO-2026-001")
                .vendorName("Al-Wadi Farms")
                .invoicedTotal(new BigDecimal("2000.00"))
                .reconciliationStatus(ReconciliationStatus.APPROVED)
                .createdAt(Instant.now())
                .build();

        ReconciliationSummaryResponse summary = new ReconciliationSummaryResponse(
                1L, "INV-101", "PO-2026-001", "Al-Wadi Farms",
                "APPROVED", new BigDecimal("2000.00"), new BigDecimal("2000.00"),
                0, Collections.emptyList(), null, "/api/invoices/1/file"
        );

        when(invoiceRepository.findWithAuditsById(1L)).thenReturn(Optional.of(invoice1));
        when(reconciliationEngineService.toSummaryResponse(invoice1)).thenReturn(summary);

        mockMvc.perform(get("/api/invoices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value(1))
                .andExpect(jsonPath("$.invoiceNumber").value("INV-101"));
    }

    @Test
    @DisplayName("GET /api/invoices/{id}/file should stream binary file")
    void shouldStreamInvoiceFile() throws Exception {
        Invoice invoice1 = Invoice.builder()
                .id(1L)
                .filePath("/uploads/doc.pdf")
                .build();

        ByteArrayResource resource = new ByteArrayResource("PDF CONTENT".getBytes()) {
            @Override
            public String getFilename() {
                return "doc.pdf";
            }
        };

        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice1));
        when(fileStorageService.loadFileAsResource("/uploads/doc.pdf")).thenReturn(resource);

        mockMvc.perform(get("/api/invoices/1/file"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF_VALUE));
    }

    @Test
    @DisplayName("POST /api/invoices/{id}/approve should mark status APPROVED")
    void shouldApproveInvoice() throws Exception {
        Invoice invoice1 = Invoice.builder()
                .id(1L)
                .invoiceNumber("INV-101")
                .reconciliationStatus(ReconciliationStatus.FLAGGED_DISCREPANCY)
                .build();

        ReconciliationSummaryResponse approvedSummary = new ReconciliationSummaryResponse(
                1L, "INV-101", "PO-2026-001", "Al-Wadi Farms",
                "APPROVED", new BigDecimal("2000.00"), new BigDecimal("2000.00"),
                0, Collections.emptyList(), null, "/api/invoices/1/file"
        );

        when(invoiceRepository.findWithAuditsById(1L)).thenReturn(Optional.of(invoice1));
        when(invoiceRepository.save(any())).thenReturn(invoice1);
        when(reconciliationEngineService.toSummaryResponse(invoice1)).thenReturn(approvedSummary);
        when(emailNotificationService.sendApprovalEmailToManager(any(), any(), any()))
                .thenReturn(new com.agent.reconciliation.service.EmailNotificationService.EmailDispatchResult(true, true, "manager@test.com", "Simulated"));

        mockMvc.perform(post("/api/invoices/1/approve")
                        .param("email", "manager@test.com")
                        .param("notes", "Price variance accepted by finance head"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Email-Dispatched", "true"))
                .andExpect(header().string("X-Email-Recipient", "manager@test.com"))
                .andExpect(jsonPath("$.reconciliationStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("POST /api/invoices/{id}/reject should mark status REJECTED and dispatch dispute email")
    void shouldRejectInvoice() throws Exception {
        Invoice invoice1 = Invoice.builder()
                .id(1L)
                .invoiceNumber("INV-101")
                .vendorName("Al-Wadi Farms")
                .reconciliationStatus(ReconciliationStatus.FLAGGED_DISCREPANCY)
                .build();

        ReconciliationSummaryResponse rejectedSummary = new ReconciliationSummaryResponse(
                1L, "INV-101", "PO-2026-001", "Al-Wadi Farms",
                "REJECTED", new BigDecimal("3200.00"), new BigDecimal("2750.00"),
                2, Collections.emptyList(), "Dispute letter draft", "/api/invoices/1/file"
        );

        when(invoiceRepository.findWithAuditsById(1L)).thenReturn(Optional.of(invoice1));
        when(invoiceRepository.save(any())).thenReturn(invoice1);
        when(reconciliationEngineService.toSummaryResponse(invoice1)).thenReturn(rejectedSummary);
        when(emailNotificationService.sendDisputeEmailToVendor(any(), any(), any()))
                .thenReturn(new com.agent.reconciliation.service.EmailNotificationService.EmailDispatchResult(true, true, "vendor@test.com", "Simulated"));

        mockMvc.perform(post("/api/invoices/1/reject")
                        .param("email", "vendor@test.com")
                        .param("lang", "BOTH"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Email-Dispatched", "true"))
                .andExpect(header().string("X-Email-Recipient", "vendor@test.com"))
                .andExpect(jsonPath("$.reconciliationStatus").value("REJECTED"));
    }

    @Test
    @DisplayName("POST /api/invoices/{id}/generate-dispute should regenerate and persist dispute draft")
    void shouldGenerateDisputeDraft() throws Exception {
        Invoice invoice1 = Invoice.builder()
                .id(1L)
                .invoiceNumber("INV-101")
                .poReference("PO-2026-001")
                .vendorName("Al-Wadi Farms")
                .reconciliationStatus(ReconciliationStatus.FLAGGED_DISCREPANCY)
                .build();

        ReconciliationSummaryResponse regeneratedSummary = new ReconciliationSummaryResponse(
                1L, "INV-101", "PO-2026-001", "Al-Wadi Farms",
                "FLAGGED_DISCREPANCY", new BigDecimal("3200.00"), new BigDecimal("2750.00"),
                2, Collections.emptyList(), "New AI Dispute Draft", "/api/invoices/1/file"
        );

        when(invoiceRepository.findWithAuditsById(1L)).thenReturn(Optional.of(invoice1));
        when(purchaseOrderRepository.findByPoNumber("PO-2026-001")).thenReturn(Optional.empty());
        when(disputeDraftingService.generateDisputeDraft(any(), any(), any())).thenReturn("New AI Dispute Draft");
        when(invoiceRepository.save(any())).thenReturn(invoice1);
        when(reconciliationEngineService.toSummaryResponse(invoice1)).thenReturn(regeneratedSummary);

        mockMvc.perform(post("/api/invoices/1/generate-dispute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disputeDraft").value("New AI Dispute Draft"));
    }
}
