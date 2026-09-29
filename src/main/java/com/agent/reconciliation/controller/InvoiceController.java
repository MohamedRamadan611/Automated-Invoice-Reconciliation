package com.agent.reconciliation.controller;

import com.agent.reconciliation.domain.dto.ExtractedInvoice;
import com.agent.reconciliation.domain.dto.InvoiceListItemResponse;
import com.agent.reconciliation.domain.dto.ReconciliationSummaryResponse;
import com.agent.reconciliation.domain.entity.Invoice;
import com.agent.reconciliation.domain.entity.ReconciliationStatus;
import com.agent.reconciliation.repository.InvoiceRepository;
import com.agent.reconciliation.service.FileStorageService;
import com.agent.reconciliation.service.InvoiceExtractionService;
import com.agent.reconciliation.service.ReconciliationEngineService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * REST API Controller managing invoice uploads, reconciliation status, and binary document streaming.
 */
@RestController
@RequestMapping("/api/invoices")
@CrossOrigin(origins = "*")
public class InvoiceController {

    private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceRepository invoiceRepository;
    private final FileStorageService fileStorageService;
    private final InvoiceExtractionService extractionService;
    private final ReconciliationEngineService reconciliationEngineService;
    private final ObjectMapper objectMapper;

    public InvoiceController(InvoiceRepository invoiceRepository,
                             FileStorageService fileStorageService,
                             InvoiceExtractionService extractionService,
                             ReconciliationEngineService reconciliationEngineService,
                             ObjectMapper objectMapper) {
        this.invoiceRepository = invoiceRepository;
        this.fileStorageService = fileStorageService;
        this.extractionService = extractionService;
        this.reconciliationEngineService = reconciliationEngineService;
        this.objectMapper = objectMapper;
    }

    /**
     * Upload an invoice document, extract structured line items, reconcile against PO, and return audit summary.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReconciliationSummaryResponse> uploadInvoice(@RequestParam("file") MultipartFile file) {
        log.info("Received invoice upload request: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        // 1. Store file locally
        String filePath = fileStorageService.storeFile(file);

        // 2. Multimodal LLM Extraction
        ExtractedInvoice extracted = extractionService.extractInvoice(file);

        // 3. Serialize extracted raw JSON payload
        String rawJson;
        try {
            rawJson = objectMapper.writeValueAsString(extracted);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize extracted invoice to JSON: {}", ex.getMessage());
            rawJson = "{}";
        }

        // 4. Deterministic Java Reconciliation against MySQL PO
        Invoice invoice = reconciliationEngineService.reconcile(extracted, filePath, rawJson);

        // 5. Build summary response
        ReconciliationSummaryResponse summary = reconciliationEngineService.toSummaryResponse(invoice);
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    /**
     * List all processed invoices with status badges and discrepancy counts.
     */
    @GetMapping
    public ResponseEntity<List<InvoiceListItemResponse>> getAllInvoices() {
        List<Invoice> invoices = invoiceRepository.findAllWithAudits();
        List<InvoiceListItemResponse> list = invoices.stream()
                .map(i -> new InvoiceListItemResponse(
                        i.getId(),
                        i.getInvoiceNumber(),
                        i.getPoReference(),
                        i.getVendorName(),
                        i.getInvoicedTotal(),
                        i.getReconciliationStatus().name(),
                        i.getAudits() != null ? i.getAudits().size() : 0,
                        i.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(list);
    }

    /**
     * Get detailed breakdown of an invoice, matching PO details, and audit records.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReconciliationSummaryResponse> getInvoiceById(@PathVariable("id") Long id) {
        Invoice invoice = invoiceRepository.findWithAuditsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found with id: " + id));

        return ResponseEntity.ok(reconciliationEngineService.toSummaryResponse(invoice));
    }

    /**
     * Stream binary PDF or image file for frontend split-screen document preview.
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> getInvoiceFile(@PathVariable("id") Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found with id: " + id));

        Resource resource = fileStorageService.loadFileAsResource(invoice.getFilePath());

        String contentType = null;
        try {
            Path path = Paths.get(invoice.getFilePath());
            contentType = Files.probeContentType(path);
        } catch (IOException ignored) {
        }

        if (contentType == null) {
            String filename = resource.getFilename() != null ? resource.getFilename().toLowerCase() : "";
            if (filename.endsWith(".pdf")) {
                contentType = MediaType.APPLICATION_PDF_VALUE;
            } else if (filename.endsWith(".png")) {
                contentType = MediaType.IMAGE_PNG_VALUE;
            } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                contentType = MediaType.IMAGE_JPEG_VALUE;
            } else {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    /**
     * Approves an invoice for payment override.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ReconciliationSummaryResponse> approveInvoice(@PathVariable("id") Long id) {
        Invoice invoice = invoiceRepository.findWithAuditsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found with id: " + id));

        invoice.setReconciliationStatus(ReconciliationStatus.APPROVED);
        Invoice saved = invoiceRepository.save(invoice);
        log.info("Invoice {} manually approved for payment.", id);

        return ResponseEntity.ok(reconciliationEngineService.toSummaryResponse(saved));
    }
}
