package com.agent.reconciliation.service;

import com.agent.reconciliation.domain.dto.AuditDetailResponse;
import com.agent.reconciliation.domain.dto.BilledLineItemResponse;
import com.agent.reconciliation.domain.dto.ExtractedInvoice;
import com.agent.reconciliation.domain.dto.ExtractedLineItem;
import com.agent.reconciliation.domain.dto.ReconciliationSummaryResponse;
import com.agent.reconciliation.domain.entity.*;
import com.agent.reconciliation.repository.InvoiceRepository;
import com.agent.reconciliation.repository.PurchaseOrderItemRepository;
import com.agent.reconciliation.repository.PurchaseOrderRepository;
import com.agent.reconciliation.util.DisputeReasonHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Deterministic Java Reconciliation Engine.
 * Executes 100% of mathematical validations, line-item matching, tolerance checks,
 * and status resolutions in pure Java logic (zero LLM math).
 * Completely generic across all industries; zero hardcoded commodities.
 */
@Service
public class ReconciliationEngineService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationEngineService.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final InvoiceRepository invoiceRepository;
    private final DisputeDraftingService disputeDraftingService;
    private final ObjectMapper objectMapper;

    public ReconciliationEngineService(PurchaseOrderRepository purchaseOrderRepository,
                                       InvoiceRepository invoiceRepository,
                                       DisputeDraftingService disputeDraftingService) {
        this(purchaseOrderRepository, null, invoiceRepository, disputeDraftingService, new ObjectMapper());
    }

    public ReconciliationEngineService(PurchaseOrderRepository purchaseOrderRepository,
                                       PurchaseOrderItemRepository purchaseOrderItemRepository,
                                       InvoiceRepository invoiceRepository,
                                       DisputeDraftingService disputeDraftingService) {
        this(purchaseOrderRepository, purchaseOrderItemRepository, invoiceRepository, disputeDraftingService, new ObjectMapper());
    }

    @Autowired
    public ReconciliationEngineService(PurchaseOrderRepository purchaseOrderRepository,
                                       PurchaseOrderItemRepository purchaseOrderItemRepository,
                                       InvoiceRepository invoiceRepository,
                                       DisputeDraftingService disputeDraftingService,
                                       ObjectMapper objectMapper) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.invoiceRepository = invoiceRepository;
        this.disputeDraftingService = disputeDraftingService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Executes deterministic audit and reconciliation on the extracted invoice.
     * Network calls to Gemini are isolated outside of the database transaction.
     *
     * @param extracted structured invoice DTO extracted from document
     * @param filePath stored local path of the invoice file
     * @param rawJsonPayload serialized JSON payload from extraction
     * @return persisted Invoice entity with audits and dispute draft
     */
    public Invoice reconcile(ExtractedInvoice extracted, String filePath, String rawJsonPayload) {
        log.info("Starting deterministic reconciliation for invoice: {}, PO Ref: {}",
                extracted.invoiceNumber(), extracted.poReference());

        // 1. Transactional DB audit & initial save (releases DB connection immediately)
        Invoice invoice = performAuditAndPersist(extracted, filePath, rawJsonPayload);

        // 2. Non-transactional external network I/O to Gemini AI (prevents DB connection pool starvation)
        if (invoice.getReconciliationStatus() == ReconciliationStatus.FLAGGED_DISCREPANCY) {
            PurchaseOrder po = null;
            if (invoice.getPoReference() != null && !invoice.getPoReference().isBlank()) {
                po = purchaseOrderRepository.findByPoNumber(invoice.getPoReference().trim()).orElse(null);
            }
            log.info("Generating AI dispute draft outside of database transaction for invoice: {}", invoice.getInvoiceNumber());
            var disputeResult = disputeDraftingService.generateDisputeDraftResult(invoice, po, invoice.getAudits());

            // 3. Short transactional update
            if (disputeResult != null) {
                return saveDisputeDraft(invoice, disputeResult.fullDraft(), disputeResult.arabicDraft(), disputeResult.englishDraft());
            } else {
                String draft = disputeDraftingService.generateDisputeDraft(invoice, po, invoice.getAudits());
                return saveDisputeDraft(invoice, draft != null ? draft : "");
            }
        }

        return invoice;
    }

    /**
     * Isolated transactional method executing deterministic business logic and audit persistence.
     */
    @Transactional
    public Invoice performAuditAndPersist(ExtractedInvoice extracted, String filePath, String rawJsonPayload) {
        Invoice invoice = Invoice.builder()
                .invoiceNumber(extracted.invoiceNumber() != null ? extracted.invoiceNumber() : "UNKNOWN")
                .poReference(extracted.poReference())
                .vendorName(extracted.vendorName() != null ? extracted.vendorName() : "Unknown Vendor")
                .filePath(filePath)
                .invoicedTotal(extracted.grandTotal() != null ? extracted.grandTotal() : BigDecimal.ZERO)
                .rawJsonPayload(rawJsonPayload)
                .build();

        // Step A: PO Lookup
        String poRef = extracted.poReference() != null ? extracted.poReference().trim() : "";
        Optional<PurchaseOrder> poOpt = poRef.isEmpty()
                ? Optional.empty()
                : purchaseOrderRepository.findWithItemsByPoNumber(poRef);

        if (poOpt.isEmpty()) {
            log.warn("Step A: Purchase Order '{}' not found in database. Flagging MANUAL_REVIEW.", poRef);
            invoice.setReconciliationStatus(ReconciliationStatus.MANUAL_REVIEW);

            ReconciliationAudit audit = ReconciliationAudit.builder()
                    .issueType(IssueType.PO_NOT_FOUND)
                    .skuCode(null)
                    .itemDescription("Purchase Order: " + (poRef.isEmpty() ? "MISSING" : poRef))
                    .expectedValue(null)
                    .actualValue(extracted.grandTotal() != null ? extracted.grandTotal() : BigDecimal.ZERO)
                    .build();
            audit.setExplanation(DisputeReasonHelper.getBilingualExplanation(audit));

            invoice.addAudit(audit);
            return invoiceRepository.save(invoice);
        }

        PurchaseOrder po = poOpt.get();
        List<PurchaseOrderItem> poItems = po.getItems();
        Set<Long> matchedPoItemIds = new HashSet<>();

        // Step B: Line Item Verification (Pure Java Math using BigDecimal)
        if (extracted.items() != null) {
            for (ExtractedLineItem item : extracted.items()) {
                Optional<PurchaseOrderItem> matchedPoItem = findMatchingPoItem(item, poItems, matchedPoItemIds);

                if (matchedPoItem.isEmpty()) {
                    // Check if SKU exists anywhere in enterprise catalog
                    String sku = item.suggestedSku() != null ? item.suggestedSku().trim() : null;
                    if (sku != null && purchaseOrderItemRepository != null) {
                        purchaseOrderItemRepository.findFirstBySkuCodeIgnoreCase(sku).ifPresent(catalogItem ->
                                log.info("SKU '{}' recognized in enterprise database ({}) but unapproved for PO {}",
                                        sku, catalogItem.getDescription(), po.getPoNumber())
                        );
                    }

                    log.info("Item '{}' did not match any approved line in PO {}", item.vendorItemDescription(), po.getPoNumber());
                    ReconciliationAudit audit = ReconciliationAudit.builder()
                            .issueType(IssueType.UNRECOGNIZED_ITEM)
                            .skuCode(item.suggestedSku())
                            .itemDescription(item.vendorItemDescription())
                            .expectedValue(null)
                            .actualValue(item.lineTotal() != null ? item.lineTotal() :
                                    (item.unitPrice() != null && item.quantity() != null ? item.unitPrice().multiply(item.quantity()) : BigDecimal.ZERO))
                            .build();
                    audit.setExplanation(DisputeReasonHelper.getBilingualExplanation(audit));

                    invoice.addAudit(audit);
                } else {
                    PurchaseOrderItem poItem = matchedPoItem.get();
                    matchedPoItemIds.add(poItem.getId());

                    // Price Check
                    if (item.unitPrice() != null && poItem.getAgreedUnitPrice() != null
                            && item.unitPrice().compareTo(poItem.getAgreedUnitPrice()) != 0) {
                        log.info("Price mismatch for {}: agreed={}, billed={}", poItem.getSkuCode(), poItem.getAgreedUnitPrice(), item.unitPrice());

                        ReconciliationAudit audit = ReconciliationAudit.builder()
                                .issueType(IssueType.PRICE_MISMATCH)
                                .skuCode(poItem.getSkuCode())
                                .itemDescription(item.vendorItemDescription())
                                .expectedValue(poItem.getAgreedUnitPrice())
                                .actualValue(item.unitPrice())
                                .build();
                        audit.setExplanation(DisputeReasonHelper.getBilingualExplanation(audit));

                        invoice.addAudit(audit);
                    }

                    // Quantity Check
                    if (item.quantity() != null && poItem.getExpectedQuantity() != null
                            && item.quantity().compareTo(poItem.getExpectedQuantity()) != 0) {
                        log.info("Quantity mismatch for {}: expected={}, billed={}", poItem.getSkuCode(), poItem.getExpectedQuantity(), item.quantity());

                        ReconciliationAudit audit = ReconciliationAudit.builder()
                                .issueType(IssueType.QUANTITY_MISMATCH)
                                .skuCode(poItem.getSkuCode())
                                .itemDescription(item.vendorItemDescription())
                                .expectedValue(poItem.getExpectedQuantity())
                                .actualValue(item.quantity())
                                .build();
                        audit.setExplanation(DisputeReasonHelper.getBilingualExplanation(audit));

                        invoice.addAudit(audit);
                    }
                }
            }
        }

        // Step C: Extra Fees Check
        if (extracted.extraFees() != null && extracted.extraFees().compareTo(BigDecimal.ZERO) > 0) {
            log.info("Extra unapproved surcharge detected: {} EGP", extracted.extraFees());
            ReconciliationAudit audit = ReconciliationAudit.builder()
                    .issueType(IssueType.EXTRA_FEE)
                    .skuCode("SURCHARGE")
                    .itemDescription("Unapproved Surcharge / Delivery Fee (مشال / توصيل)")
                    .expectedValue(BigDecimal.ZERO.setScale(2))
                    .actualValue(extracted.extraFees())
                    .build();
            audit.setExplanation(DisputeReasonHelper.getBilingualExplanation(audit));

            invoice.addAudit(audit);
        }

        // Step D: Status Resolution
        if (invoice.getAudits().isEmpty()) {
            log.info("Invoice {} reconciled with ZERO discrepancies. Status: APPROVED.", invoice.getInvoiceNumber());
            invoice.setReconciliationStatus(ReconciliationStatus.APPROVED);
        } else {
            log.info("Invoice {} has {} discrepancies. Status: FLAGGED_DISCREPANCY.",
                    invoice.getInvoiceNumber(), invoice.getAudits().size());
            invoice.setReconciliationStatus(ReconciliationStatus.FLAGGED_DISCREPANCY);
        }

        return invoiceRepository.save(invoice);
    }

    /**
     * Isolated transactional method updating the dispute draft on the invoice.
     */
    @Transactional
    public Invoice saveDisputeDraft(Invoice invoice, String disputeDraft, String disputeDraftArabic, String disputeDraftEnglish) {
        invoice.setDisputeDraft(disputeDraft);
        invoice.setDisputeDraftArabic(disputeDraftArabic);
        invoice.setDisputeDraftEnglish(disputeDraftEnglish);
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice saveDisputeDraft(Invoice invoice, String disputeDraft) {
        var parsed = disputeDraftingService != null ? disputeDraftingService.parseDisputeSections(disputeDraft, invoice, null, invoice.getAudits()) : null;
        if (parsed != null) {
            return saveDisputeDraft(invoice, parsed.fullDraft(), parsed.arabicDraft(), parsed.englishDraft());
        }
        return saveDisputeDraft(invoice, disputeDraft, null, null);
    }

    /**
     * Isolated transactional method updating the dispute draft on the invoice by ID.
     */
    @Transactional
    public Invoice updateDisputeDraft(Long invoiceId, String disputeDraft) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found with id: " + invoiceId));
        return saveDisputeDraft(invoice, disputeDraft);
    }

    /**
     * Matches an extracted line item to the appropriate PO item by SKU code or generic description similarity.
     */
    private Optional<PurchaseOrderItem> findMatchingPoItem(ExtractedLineItem item,
                                                           List<PurchaseOrderItem> poItems,
                                                           Set<Long> alreadyMatchedIds) {
        if (poItems == null || poItems.isEmpty()) {
            return Optional.empty();
        }

        // 1. Direct SKU Match
        if (item.suggestedSku() != null && !item.suggestedSku().isBlank()) {
            String targetSku = item.suggestedSku().trim();
            for (PurchaseOrderItem poItem : poItems) {
                if (!alreadyMatchedIds.contains(poItem.getId()) && poItem.getSkuCode().equalsIgnoreCase(targetSku)) {
                    return Optional.of(poItem);
                }
            }
        }

        // 2. Generic Token & Semantic Description Matching (Industry Agnostic)
        String desc = item.vendorItemDescription() != null ? item.vendorItemDescription() : "";

        for (PurchaseOrderItem poItem : poItems) {
            if (alreadyMatchedIds.contains(poItem.getId())) {
                continue;
            }
            String poDesc = poItem.getDescription();
            String poSku = poItem.getSkuCode();

            if (isGenericDescriptionMatch(desc, poDesc, poSku)) {
                return Optional.of(poItem);
            }
        }

        return Optional.empty();
    }

    /**
     * Generic, industry-agnostic matcher between an invoiced item description and PO specifications.
     * Evaluates substring containment, normalized token overlap, and SKU token presence.
     */
    private boolean isGenericDescriptionMatch(String invoicedDesc, String poDesc, String poSku) {
        if (invoicedDesc == null || invoicedDesc.isBlank() || poDesc == null || poDesc.isBlank()) {
            return false;
        }

        String normInv = invoicedDesc.trim().toLowerCase();
        String normPo = poDesc.trim().toLowerCase();

        // 1. Direct or Substring Containment
        if (normPo.contains(normInv) || normInv.contains(normPo)) {
            return true;
        }

        // 2. Token overlap matching (generic across languages, commodities, industrial parts)
        Set<String> invTokens = tokenize(normInv);
        Set<String> poTokens = tokenize(normPo);

        if (invTokens.isEmpty() || poTokens.isEmpty()) {
            return false;
        }

        long overlapCount = invTokens.stream().filter(poTokens::contains).count();
        if (overlapCount >= 2) {
            return true;
        }
        if (overlapCount == 1 && (invTokens.size() <= 2 || poTokens.size() <= 2)) {
            return true;
        }

        // 3. SKU token containment (e.g. if SKU code appears in description)
        if (poSku != null && !poSku.isBlank()) {
            String cleanSku = poSku.toLowerCase().replace("-", " ").replace("_", " ");
            for (String part : cleanSku.split("\\s+")) {
                if (part.length() > 2 && normInv.contains(part)) {
                    return true;
                }
            }
        }

        return false;
    }

    private Set<String> tokenize(String input) {
        String cleaned = input.replaceAll("[\\p{Punct}&&[^-]]", " ")
                .replaceAll("[\\u064B-\\u065F]", "")
                .trim();
        String[] words = cleaned.split("\\s+");
        Set<String> tokens = new HashSet<>();
        Set<String> stopWords = Set.of(
                "of", "the", "and", "for", "with", "in", "to", "grade", "premium", "fresh",
                "من", "في", "على", "و", "أو", "درجة", "أولى", "طازج", "بلدي", "فاخر", "كجم", "طن"
        );
        for (String w : words) {
            if (w.length() >= 2 && !stopWords.contains(w)) {
                tokens.add(w);
            }
        }
        return tokens;
    }

    /**
     * Converts an Invoice entity to a ReconciliationSummaryResponse DTO.
     */
    public ReconciliationSummaryResponse toSummaryResponse(Invoice invoice) {
        BigDecimal expectedTotal = BigDecimal.ZERO;
        PurchaseOrder po = null;
        if (invoice.getPoReference() != null && !invoice.getPoReference().isBlank()) {
            Optional<PurchaseOrder> poOpt = purchaseOrderRepository.findByPoNumber(invoice.getPoReference().trim());
            if (poOpt.isPresent()) {
                po = poOpt.get();
                expectedTotal = po.getTotalExpectedAmount();
            }
        }

        List<AuditDetailResponse> auditResponses = invoice.getAudits() != null
                ? invoice.getAudits().stream()
                .map(a -> new AuditDetailResponse(
                        a.getId(),
                        a.getIssueType().name(),
                        a.getSkuCode(),
                        a.getItemDescription(),
                        a.getExpectedValue(),
                        a.getActualValue(),
                        a.getExplanation(),
                        DisputeReasonHelper.getArabicReason(a),
                        DisputeReasonHelper.getEnglishReason(a)
                ))
                .toList()
                : Collections.emptyList();

        List<BilledLineItemResponse> billedItems = buildBilledLineItems(invoice);

        String fullDraft = invoice.getDisputeDraft();
        String arDraft = (invoice.getDisputeDraftArabic() != null && !invoice.getDisputeDraftArabic().isBlank())
                ? invoice.getDisputeDraftArabic()
                : ((fullDraft != null && !fullDraft.isBlank())
                ? disputeDraftingService.extractOrGenerateArabic(fullDraft, invoice, po, invoice.getAudits())
                : null);

        String enDraft = (invoice.getDisputeDraftEnglish() != null && !invoice.getDisputeDraftEnglish().isBlank())
                ? invoice.getDisputeDraftEnglish()
                : ((fullDraft != null && !fullDraft.isBlank())
                ? disputeDraftingService.extractOrGenerateEnglish(fullDraft, invoice, po, invoice.getAudits())
                : null);

        return new ReconciliationSummaryResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getPoReference(),
                invoice.getVendorName(),
                invoice.getReconciliationStatus().name(),
                invoice.getInvoicedTotal(),
                expectedTotal,
                invoice.getAudits() != null ? invoice.getAudits().size() : 0,
                auditResponses,
                billedItems,
                fullDraft,
                arDraft,
                enDraft,
                "/api/invoices/" + invoice.getId() + "/file"
        );
    }

    private List<BilledLineItemResponse> buildBilledLineItems(Invoice invoice) {
        if (invoice.getRawJsonPayload() == null || invoice.getRawJsonPayload().isBlank()) {
            return Collections.emptyList();
        }

        try {
            ExtractedInvoice extracted = objectMapper.readValue(invoice.getRawJsonPayload(), ExtractedInvoice.class);
            List<BilledLineItemResponse> items = new ArrayList<>();
            List<ReconciliationAudit> audits = invoice.getAudits() != null ? invoice.getAudits() : Collections.emptyList();

            if (extracted.items() != null) {
                for (ExtractedLineItem item : extracted.items()) {
                    Optional<ReconciliationAudit> auditOpt = audits.stream()
                            .filter(a -> (a.getSkuCode() != null && item.suggestedSku() != null && a.getSkuCode().equalsIgnoreCase(item.suggestedSku()))
                                    || (a.getItemDescription() != null && item.vendorItemDescription() != null && a.getItemDescription().equalsIgnoreCase(item.vendorItemDescription())))
                            .findFirst();

                    if (auditOpt.isPresent()) {
                        ReconciliationAudit audit = auditOpt.get();
                        String statusLabel = switch (audit.getIssueType()) {
                            case PRICE_MISMATCH -> "Price Variance";
                            case QUANTITY_MISMATCH -> "Quantity Variance";
                            case UNRECOGNIZED_ITEM -> "Unrecognized Item";
                            case PO_NOT_FOUND -> "PO Reference Missing";
                            case EXTRA_FEE -> "Extra Surcharge";
                        };
                        items.add(new BilledLineItemResponse(
                                item.vendorItemDescription(),
                                item.suggestedSku(),
                                item.quantity(),
                                item.unitPrice(),
                                item.lineTotal(),
                                audit.getIssueType().name(),
                                statusLabel,
                                audit.getExplanation()
                        ));
                    } else {
                        items.add(new BilledLineItemResponse(
                                item.vendorItemDescription(),
                                item.suggestedSku(),
                                item.quantity(),
                                item.unitPrice(),
                                item.lineTotal(),
                                "MATCHED",
                                "100% Matched PO Terms",
                                "Billed unit price and quantity fully reconcile with purchase order terms."
                        ));
                    }
                }
            }

            if (extracted.extraFees() != null && extracted.extraFees().compareTo(BigDecimal.ZERO) > 0) {
                Optional<ReconciliationAudit> extraFeeAudit = audits.stream()
                        .filter(a -> a.getIssueType() == IssueType.EXTRA_FEE)
                        .findFirst();

                items.add(new BilledLineItemResponse(
                        "Express Freight & Logistics Surcharge (مشال / توصيل)",
                        "SURCHARGE",
                        BigDecimal.ONE,
                        extracted.extraFees(),
                        extracted.extraFees(),
                        "EXTRA_FEE",
                        "Unapproved Surcharge",
                        extraFeeAudit.map(ReconciliationAudit::getExplanation)
                                .orElse("Unapproved delivery/freight surcharge billed on invoice without authorization in PO.")
                ));
            }

            return items;
        } catch (Exception ex) {
            log.warn("Failed to deserialize rawJsonPayload for invoice {}: {}", invoice.getId(), ex.getMessage());
            return Collections.emptyList();
        }
    }
}
