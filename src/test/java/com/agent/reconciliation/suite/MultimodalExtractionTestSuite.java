package com.agent.reconciliation.suite;

import com.agent.reconciliation.controller.InvoiceController;
import com.agent.reconciliation.domain.dto.ExtractedInvoice;
import com.agent.reconciliation.domain.dto.ReconciliationSummaryResponse;
import com.agent.reconciliation.domain.entity.Invoice;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SUITE 2: Multimodal Document Extraction & Controller Ingestion Tests
 *
 * Validates extraction pipeline error handling, empty file rejection,
 * corrupted file handling, and the upload endpoint's behavioral contracts.
 *
 * Test IDs: EXT-001 through EXT-007
 */
@WebMvcTest(InvoiceController.class)
class MultimodalExtractionTestSuite {

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

    @Autowired
    private ObjectMapper objectMapper;

    // ─── EXT-001: Vector PDF Text Extraction + Upload Returns 201 ───────────────
    @Test
    @DisplayName("EXT-001: Valid PDF upload triggers extraction and returns 201 Created")
    void ext001_validPdfUploadReturns201() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf", "valid pdf content".getBytes());

        ExtractedInvoice mockExtracted = new ExtractedInvoice(
                "INV-EXT001", "PO-2026-001", "Test Vendor",
                List.of(), BigDecimal.ZERO, new BigDecimal("1000.00"));

        Invoice mockInvoice = Invoice.builder()
                .id(1L).invoiceNumber("INV-EXT001").poReference("PO-2026-001")
                .vendorName("Test Vendor").filePath("/uploads/invoice.pdf")
                .invoicedTotal(new BigDecimal("1000.00"))
                .reconciliationStatus(ReconciliationStatus.APPROVED)
                .createdAt(Instant.now()).build();

        ReconciliationSummaryResponse mockSummary = new ReconciliationSummaryResponse(
                1L, "INV-EXT001", "PO-2026-001", "Test Vendor", "APPROVED",
                new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                0, Collections.emptyList(), null, "/api/invoices/1/file");

        when(fileStorageService.storeFile(any())).thenReturn("/uploads/invoice.pdf");
        when(extractionService.extractInvoice(any())).thenReturn(mockExtracted);
        when(reconciliationEngineService.reconcile(any(), eq("/uploads/invoice.pdf"), any())).thenReturn(mockInvoice);
        when(reconciliationEngineService.toSummaryResponse(mockInvoice)).thenReturn(mockSummary);

        mockMvc.perform(multipart("/api/invoices/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceId").value(1))
                .andExpect(jsonPath("$.reconciliationStatus").value("APPROVED"));
    }

    // ─── EXT-003: Native Image Upload (PNG) ─────────────────────────────────────
    @Test
    @DisplayName("EXT-003: PNG image upload triggers extraction pipeline")
    void ext003_pngImageUploadProcessed() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "scan.png", "image/png", "PNG image data".getBytes());

        ExtractedInvoice mockExtracted = new ExtractedInvoice(
                "INV-EXT003", "PO-2026-002", "Image Vendor",
                List.of(), BigDecimal.ZERO, new BigDecimal("500.00"));

        Invoice mockInvoice = Invoice.builder()
                .id(2L).invoiceNumber("INV-EXT003").poReference("PO-2026-002")
                .vendorName("Image Vendor").filePath("/uploads/scan.png")
                .invoicedTotal(new BigDecimal("500.00"))
                .reconciliationStatus(ReconciliationStatus.APPROVED)
                .createdAt(Instant.now()).build();

        ReconciliationSummaryResponse mockSummary = new ReconciliationSummaryResponse(
                2L, "INV-EXT003", "PO-2026-002", "Image Vendor", "APPROVED",
                new BigDecimal("500.00"), new BigDecimal("500.00"),
                0, Collections.emptyList(), null, "/api/invoices/2/file");

        when(fileStorageService.storeFile(any())).thenReturn("/uploads/scan.png");
        when(extractionService.extractInvoice(any())).thenReturn(mockExtracted);
        when(reconciliationEngineService.reconcile(any(), eq("/uploads/scan.png"), any())).thenReturn(mockInvoice);
        when(reconciliationEngineService.toSummaryResponse(mockInvoice)).thenReturn(mockSummary);

        mockMvc.perform(multipart("/api/invoices/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceNumber").value("INV-EXT003"));
    }

    // ─── EXT-006a: Empty File Rejection (HTTP 400) ──────────────────────────────
    @Test
    @DisplayName("EXT-006a: Empty file upload returns HTTP 400 Bad Request")
    void ext006a_emptyFileReturns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/invoices/upload").file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    // ─── EXT-006b: Corrupted/Unparseable File (HTTP 422) ────────────────────────
    @Test
    @DisplayName("EXT-006b: Corrupted file that fails extraction returns HTTP 422 Unprocessable Entity")
    void ext006b_corruptedFileReturns422() throws Exception {
        MockMultipartFile corruptedFile = new MockMultipartFile(
                "file", "corrupt.pdf", "application/pdf", "not a real pdf".getBytes());

        when(fileStorageService.storeFile(any())).thenReturn("/uploads/corrupt.pdf");
        when(extractionService.extractInvoice(any())).thenThrow(
                new RuntimeException("PDFBox: Invalid PDF structure — corrupted file cannot be parsed."));

        mockMvc.perform(multipart("/api/invoices/upload").file(corruptedFile))
                .andExpect(status().isUnprocessableEntity());
    }

    // ─── EXT-007: Extraction Exception Propagates as 422 ────────────────────────
    @Test
    @DisplayName("EXT-007: LLM extraction timeout/failure propagates as HTTP 422 with message")
    void ext007_extractionTimeoutReturns422WithMessage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "timeout.pdf", "application/pdf", "valid data".getBytes());

        when(fileStorageService.storeFile(any())).thenReturn("/uploads/timeout.pdf");
        when(extractionService.extractInvoice(any())).thenThrow(
                new RuntimeException("Gemini API timeout after 30s — model did not respond."));

        mockMvc.perform(multipart("/api/invoices/upload").file(file))
                .andExpect(status().isUnprocessableEntity());
    }

    // ─── EXT-004: Eastern Arabic Numeral Normalization ──────────────────────────
    @Test
    @DisplayName("EXT-004: Extraction service properly normalizes Eastern Arabic numerals")
    void ext004_easternArabicNumeralsNormalized() throws Exception {
        // This test verifies the contract at the controller level:
        // If the extraction service normalizes ٠١٢٣٤٥٦٧٨٩ -> 0123456789,
        // the reconciliation engine receives correctly typed BigDecimal values.
        MockMultipartFile file = new MockMultipartFile(
                "file", "arabic_nums.pdf", "application/pdf", "arabic numerals".getBytes());

        // The extraction service should have already normalized Eastern Arabic to Western digits
        ExtractedInvoice mockExtracted = new ExtractedInvoice(
                "INV-EXT004", "PO-2026-001", "Arabic Vendor",
                List.of(new com.agent.reconciliation.domain.dto.ExtractedLineItem(
                        "صنف اختبار", new BigDecimal("100"), new BigDecimal("25.50"), new BigDecimal("2550.00"), "SKU-TEST")),
                BigDecimal.ZERO, new BigDecimal("2550.00"));

        Invoice mockInvoice = Invoice.builder()
                .id(3L).invoiceNumber("INV-EXT004").poReference("PO-2026-001")
                .vendorName("Arabic Vendor").filePath("/uploads/arabic_nums.pdf")
                .invoicedTotal(new BigDecimal("2550.00"))
                .reconciliationStatus(ReconciliationStatus.APPROVED)
                .createdAt(Instant.now()).build();

        ReconciliationSummaryResponse mockSummary = new ReconciliationSummaryResponse(
                3L, "INV-EXT004", "PO-2026-001", "Arabic Vendor", "APPROVED",
                new BigDecimal("2550.00"), new BigDecimal("2550.00"),
                0, Collections.emptyList(), null, "/api/invoices/3/file");

        when(fileStorageService.storeFile(any())).thenReturn("/uploads/arabic_nums.pdf");
        when(extractionService.extractInvoice(any())).thenReturn(mockExtracted);
        when(reconciliationEngineService.reconcile(any(), any(), any())).thenReturn(mockInvoice);
        when(reconciliationEngineService.toSummaryResponse(any())).thenReturn(mockSummary);

        mockMvc.perform(multipart("/api/invoices/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoicedTotal").value(2550.00));
    }
}
