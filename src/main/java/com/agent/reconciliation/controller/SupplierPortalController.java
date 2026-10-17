package com.agent.reconciliation.controller;

import com.agent.reconciliation.domain.dto.*;
import com.agent.reconciliation.domain.entity.*;
import com.agent.reconciliation.repository.InvoiceRepository;
import com.agent.reconciliation.repository.PurchaseOrderRepository;
import com.agent.reconciliation.service.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Public REST Controller serving the Supplier Self-Service Dispute Resolution Portal.
 * Secured by time-bounded (7 days) HMAC-SHA256 magic link tokens.
 */
@RestController
@RequestMapping("/api/portal/dispute")
@CrossOrigin(origins = "*")
public class SupplierPortalController {

    private static final Logger log = LoggerFactory.getLogger(SupplierPortalController.class);

    private final InvoiceRepository invoiceRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final HmacTokenService hmacTokenService;
    private final FileStorageService fileStorageService;
    private final InvoiceExtractionService extractionService;
    private final ReconciliationEngineService reconciliationEngineService;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final String modelName;
    private final Resource negotiationPromptResource;

    public SupplierPortalController(InvoiceRepository invoiceRepository,
                                    PurchaseOrderRepository purchaseOrderRepository,
                                    HmacTokenService hmacTokenService,
                                    FileStorageService fileStorageService,
                                    InvoiceExtractionService extractionService,
                                    ReconciliationEngineService reconciliationEngineService,
                                    ObjectMapper objectMapper) {
        this(invoiceRepository, purchaseOrderRepository, hmacTokenService, fileStorageService,
                extractionService, reconciliationEngineService, objectMapper, null,
                "gemini-3.8-flash", new ClassPathResource("prompts/gemini-portal-negotiation.st"));
    }

    @Autowired
    public SupplierPortalController(InvoiceRepository invoiceRepository,
                                    PurchaseOrderRepository purchaseOrderRepository,
                                    HmacTokenService hmacTokenService,
                                    FileStorageService fileStorageService,
                                    InvoiceExtractionService extractionService,
                                    ReconciliationEngineService reconciliationEngineService,
                                    ObjectMapper objectMapper,
                                    @Autowired(required = false) ChatClient.Builder chatClientBuilder,
                                    @Value("${spring.ai.openai.chat.options.model:gemini-3.8-flash}") String modelName,
                                    @Value("classpath:prompts/gemini-portal-negotiation.st") Resource negotiationPromptResource) {
        this.invoiceRepository = invoiceRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.hmacTokenService = hmacTokenService;
        this.fileStorageService = fileStorageService;
        this.extractionService = extractionService;
        this.reconciliationEngineService = reconciliationEngineService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.chatClient = chatClientBuilder != null ? chatClientBuilder.build() : null;
        this.modelName = modelName;
        this.negotiationPromptResource = negotiationPromptResource;
    }

    /**
     * Authenticates the supplier token and returns the restricted dispute view.
     */
    @GetMapping
    public ResponseEntity<SupplierDisputePortalResponse> getPortalDispute(@RequestParam("token") String token) {
        Invoice invoice = validateAndGetInvoice(token);
        return ResponseEntity.ok(buildPortalResponse(invoice));
    }

    /**
     * Supplier formally accepts the deduction; transitions invoice to RESOLVED_BY_DEBIT_NOTE.
     */
    @PostMapping("/accept")
    public ResponseEntity<PortalAcceptResponse> acceptDeduction(@RequestParam("token") String token) {
        Invoice invoice = validateAndGetInvoice(token);
        log.info("Supplier accepted dispute deduction for invoice {}. Issuing Debit Note.", invoice.getInvoiceNumber());

        invoice.setReconciliationStatus(ReconciliationStatus.RESOLVED_BY_DEBIT_NOTE);
        Invoice saved = invoiceRepository.save(invoice);

        BigDecimal deduction = calculateTotalDeduction(saved);
        BigDecimal invoicedTotal = saved.getInvoicedTotal() != null ? saved.getInvoicedTotal() : BigDecimal.ZERO;
        BigDecimal netPayable = invoicedTotal.subtract(deduction).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        return ResponseEntity.ok(new PortalAcceptResponse(
                true,
                "Dispute deduction accepted. Official Debit Note generated and invoice marked for payment release.",
                saved.getId(),
                saved.getInvoiceNumber(),
                saved.getReconciliationStatus().name(),
                netPayable,
                "/api/invoices/" + saved.getId() + "/debit-note"
        ));
    }

    /**
     * Supplier submits counter-explanation; evaluated in real time by AI Negotiation Assistant.
     */
    @PostMapping(value = "/explain", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NegotiationAssessmentResponse> submitExplanation(
            @RequestParam("token") String token,
            @RequestBody Map<String, String> request) {
        Invoice invoice = validateAndGetInvoice(token);
        String explanation = request.getOrDefault("explanation", "").trim();

        if (explanation.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier explanation cannot be empty.");
        }

        log.info("Evaluating supplier explanation for invoice {}: '{}'", invoice.getInvoiceNumber(), explanation);
        NegotiationAssessmentResponse assessment = evaluateNegotiation(invoice, explanation);
        return ResponseEntity.ok(assessment);
    }

    /**
     * Supplier uploads a revised/amended invoice document; re-runs extraction and deterministic reconciliation.
     */
    @PostMapping(value = "/upload-amended", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SupplierDisputePortalResponse> uploadAmendedInvoice(
            @RequestParam("token") String token,
            @RequestParam("file") MultipartFile file) {
        Invoice existingInvoice = validateAndGetInvoice(token);

        if (file.isEmpty() || file.getSize() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded replacement file is empty.");
        }

        log.info("Ingesting amended invoice document from portal for invoice {}: {} ({} bytes)",
                existingInvoice.getInvoiceNumber(), file.getOriginalFilename(), file.getSize());

        String filePath = fileStorageService.storeFile(file);

        ExtractedInvoice extracted;
        try {
            extracted = extractionService.extractInvoice(file);
        } catch (Exception ex) {
            log.error("Amended invoice extraction failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Failed to extract amended invoice: " + ex.getMessage());
        }

        String rawJson;
        try {
            rawJson = objectMapper.writeValueAsString(extracted);
        } catch (JsonProcessingException ex) {
            rawJson = "{}";
        }

        Invoice reconciled = reconciliationEngineService.reconcile(extracted, filePath, rawJson);
        return ResponseEntity.ok(buildPortalResponse(reconciled));
    }

    private Invoice validateAndGetInvoice(String token) {
        HmacTokenService.TokenVerificationResult verification = hmacTokenService.verifyPortalToken(token);

        if (!verification.valid()) {
            if (verification.expired()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Portal access link has expired. Please request a new dispute link.");
            }
            if (verification.tampered()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or tampered portal access link.");
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, verification.message());
        }

        Long invoiceId = verification.invoiceId();
        return invoiceRepository.findWithAuditsById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found with id: " + invoiceId));
    }

    private SupplierDisputePortalResponse buildPortalResponse(Invoice invoice) {
        PurchaseOrder po = null;
        if (invoice.getPoReference() != null && !invoice.getPoReference().isBlank()) {
            po = purchaseOrderRepository.findByPoNumber(invoice.getPoReference().trim()).orElse(null);
        }

        ExtractedInvoice extracted = parseExtractedInvoice(invoice.getRawJsonPayload());
        List<ReconciliationAudit> audits = invoice.getAudits() != null ? invoice.getAudits() : Collections.emptyList();

        List<PortalDiscrepancyItem> discrepancyItems = new ArrayList<>();
        BigDecimal totalDeduction = BigDecimal.ZERO;

        for (ReconciliationAudit audit : audits) {
            BigDecimal deduction = calculateItemDeduction(audit, extracted);
            totalDeduction = totalDeduction.add(deduction);

            discrepancyItems.add(new PortalDiscrepancyItem(
                    audit.getIssueType(),
                    audit.getSkuCode(),
                    audit.getItemDescription(),
                    audit.getActualValue(),
                    audit.getExpectedValue(),
                    deduction,
                    audit.getExplanation()
            ));
        }

        totalDeduction = totalDeduction.setScale(2, RoundingMode.HALF_UP);
        BigDecimal invoicedTotal = invoice.getInvoicedTotal() != null ? invoice.getInvoicedTotal() : BigDecimal.ZERO;
        BigDecimal poExpectedTotal = po != null ? po.getTotalExpectedAmount() : BigDecimal.ZERO;
        BigDecimal netPayable = invoicedTotal.subtract(totalDeduction).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        boolean isResolved = invoice.getReconciliationStatus() == ReconciliationStatus.RESOLVED_BY_DEBIT_NOTE
                || invoice.getReconciliationStatus() == ReconciliationStatus.APPROVED;

        return new SupplierDisputePortalResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getPoReference(),
                invoice.getVendorName(),
                invoicedTotal,
                poExpectedTotal,
                totalDeduction,
                netPayable,
                invoice.getReconciliationStatus().name(),
                discrepancyItems,
                invoice.getDisputeDraftArabic(),
                invoice.getDisputeDraftEnglish(),
                isResolved,
                "/api/invoices/" + invoice.getId() + "/debit-note"
        );
    }

    private BigDecimal calculateTotalDeduction(Invoice invoice) {
        ExtractedInvoice extracted = parseExtractedInvoice(invoice.getRawJsonPayload());
        List<ReconciliationAudit> audits = invoice.getAudits() != null ? invoice.getAudits() : Collections.emptyList();
        BigDecimal total = BigDecimal.ZERO;
        for (ReconciliationAudit audit : audits) {
            total = total.add(calculateItemDeduction(audit, extracted));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateItemDeduction(ReconciliationAudit audit, ExtractedInvoice extracted) {
        if (audit.getIssueType() == null) return BigDecimal.ZERO;
        BigDecimal actual = audit.getActualValue() != null ? audit.getActualValue() : BigDecimal.ZERO;
        BigDecimal expected = audit.getExpectedValue() != null ? audit.getExpectedValue() : BigDecimal.ZERO;

        return switch (audit.getIssueType()) {
            case PRICE_MISMATCH -> {
                BigDecimal delta = actual.subtract(expected);
                if (delta.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal qty = resolveQuantity(audit, extracted);
                    yield delta.multiply(qty).setScale(2, RoundingMode.HALF_UP);
                }
                yield BigDecimal.ZERO;
            }
            case EXTRA_FEE -> {
                BigDecimal fee = actual.subtract(expected);
                yield (fee.compareTo(BigDecimal.ZERO) > 0 ? fee : actual).setScale(2, RoundingMode.HALF_UP);
            }
            case UNRECOGNIZED_ITEM -> actual.setScale(2, RoundingMode.HALF_UP);
            case QUANTITY_MISMATCH -> {
                BigDecimal delta = actual.subtract(expected);
                if (delta.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal unitPrice = resolveUnitPrice(audit, extracted);
                    yield delta.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
                }
                yield BigDecimal.ZERO;
            }
            case PO_NOT_FOUND -> BigDecimal.ZERO;
        };
    }

    private NegotiationAssessmentResponse evaluateNegotiation(Invoice invoice, String supplierExplanation) {
        StringBuilder varSummary = new StringBuilder();
        if (invoice.getAudits() != null) {
            for (ReconciliationAudit a : invoice.getAudits()) {
                varSummary.append(String.format("- [%s] %s: Billed %s vs Expected %s\n",
                        a.getIssueType(), a.getItemDescription(), a.getActualValue(), a.getExpectedValue()));
            }
        }

        if (chatClient != null && negotiationPromptResource != null && negotiationPromptResource.exists()) {
            try {
                OpenAiChatOptions options = OpenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(0.3)
                        .build();

                String response = chatClient.prompt()
                        .system("You are an autonomous AI Commercial Auditor. Analyze the supplier explanation against the PO terms and deliver a crisp assessment.")
                        .user(u -> u.text(negotiationPromptResource)
                                .param("vendorName", invoice.getVendorName())
                                .param("invoiceNumber", invoice.getInvoiceNumber())
                                .param("poReference", invoice.getPoReference() != null ? invoice.getPoReference() : "N/A")
                                .param("invoicedTotal", invoice.getInvoicedTotal() != null ? invoice.getInvoicedTotal().toPlainString() : "0.00")
                                .param("poExpectedTotal", "PO Terms")
                                .param("variances", varSummary.toString())
                                .param("supplierExplanation", supplierExplanation))
                        .options(options)
                        .call()
                        .content();

                boolean valid = response != null && (response.toUpperCase().contains("ACCEPT") || response.toUpperCase().contains("VALID"));
                return new NegotiationAssessmentResponse(valid, response, "Audit assessment compiled by Gemini 3.8 Flash.");
            } catch (Exception ex) {
                log.warn("Gemini negotiation call failed: {}. Using deterministic assessment.", ex.getMessage());
            }
        }

        return new NegotiationAssessmentResponse(
                false,
                "Supplier submitted counter-explanation: \"" + supplierExplanation + "\". " +
                "Preliminary audit indicates contract terms govern pricing. Recommend upholding deduction or requesting formal PO revision order.",
                "Contractual terms binding. Require written PO amendment from procurement before releasing deduction hold."
        );
    }

    private ExtractedInvoice parseExtractedInvoice(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return null;
        try {
            return objectMapper.readValue(rawJson, ExtractedInvoice.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal resolveQuantity(ReconciliationAudit audit, ExtractedInvoice extracted) {
        if (extracted != null && extracted.items() != null) {
            for (ExtractedLineItem item : extracted.items()) {
                boolean skuMatch = audit.getSkuCode() != null && item.suggestedSku() != null
                        && audit.getSkuCode().equalsIgnoreCase(item.suggestedSku());
                boolean descMatch = audit.getItemDescription() != null && item.vendorItemDescription() != null
                        && audit.getItemDescription().equalsIgnoreCase(item.vendorItemDescription());

                if ((skuMatch || descMatch) && item.quantity() != null && item.quantity().compareTo(BigDecimal.ZERO) > 0) {
                    return item.quantity();
                }
            }
        }
        return BigDecimal.ONE;
    }

    private BigDecimal resolveUnitPrice(ReconciliationAudit audit, ExtractedInvoice extracted) {
        if (extracted != null && extracted.items() != null) {
            for (ExtractedLineItem item : extracted.items()) {
                boolean skuMatch = audit.getSkuCode() != null && item.suggestedSku() != null
                        && audit.getSkuCode().equalsIgnoreCase(item.suggestedSku());
                boolean descMatch = audit.getItemDescription() != null && item.vendorItemDescription() != null
                        && audit.getItemDescription().equalsIgnoreCase(item.vendorItemDescription());

                if ((skuMatch || descMatch) && item.unitPrice() != null && item.unitPrice().compareTo(BigDecimal.ZERO) > 0) {
                    return item.unitPrice();
                }
            }
        }
        return audit.getExpectedValue() != null ? audit.getExpectedValue() : BigDecimal.ONE;
    }
}
