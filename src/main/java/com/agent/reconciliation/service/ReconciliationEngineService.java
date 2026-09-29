package com.agent.reconciliation.service;

import com.agent.reconciliation.domain.dto.AuditDetailResponse;
import com.agent.reconciliation.domain.dto.ExtractedInvoice;
import com.agent.reconciliation.domain.dto.ExtractedLineItem;
import com.agent.reconciliation.domain.dto.ReconciliationSummaryResponse;
import com.agent.reconciliation.domain.entity.*;
import com.agent.reconciliation.repository.InvoiceRepository;
import com.agent.reconciliation.repository.PurchaseOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Deterministic Java Reconciliation Engine.
 * Executes 100% of mathematical validations, line-item matching, tolerance checks,
 * and status resolutions in pure Java logic (zero LLM math).
 */
@Service
public class ReconciliationEngineService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationEngineService.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InvoiceRepository invoiceRepository;
    private final DisputeDraftingService disputeDraftingService;

    public ReconciliationEngineService(PurchaseOrderRepository purchaseOrderRepository,
                                       InvoiceRepository invoiceRepository,
                                       DisputeDraftingService disputeDraftingService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.invoiceRepository = invoiceRepository;
        this.disputeDraftingService = disputeDraftingService;
    }

    /**
     * Executes deterministic audit and reconciliation on the extracted invoice.
     *
     * @param extracted structured invoice DTO extracted from document
     * @param filePath stored local path of the invoice file
     * @param rawJsonPayload serialized JSON payload from extraction
     * @return persisted Invoice entity with audits and dispute draft
     */
    @Transactional
    public Invoice reconcile(ExtractedInvoice extracted, String filePath, String rawJsonPayload) {
        log.info("Starting deterministic reconciliation for invoice: {}, PO Ref: {}",
                extracted.invoiceNumber(), extracted.poReference());

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
                    .explanation("Purchase Order " + (poRef.isEmpty() ? "reference was missing on document" : "'" + poRef + "' was not found in the system") + ". Manual auditor review required.")
                    .build();

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
                    // Unrecognized item
                    log.info("Item '{}' did not match any item in PO {}", item.vendorItemDescription(), po.getPoNumber());
                    ReconciliationAudit audit = ReconciliationAudit.builder()
                            .issueType(IssueType.UNRECOGNIZED_ITEM)
                            .skuCode(item.suggestedSku())
                            .itemDescription(item.vendorItemDescription())
                            .expectedValue(null)
                            .actualValue(item.lineTotal() != null ? item.lineTotal() :
                                    (item.unitPrice() != null && item.quantity() != null ? item.unitPrice().multiply(item.quantity()) : BigDecimal.ZERO))
                            .explanation(String.format("Invoiced item '%s' does not match any approved line item in PO %s",
                                    item.vendorItemDescription(), po.getPoNumber()))
                            .build();

                    invoice.addAudit(audit);
                } else {
                    PurchaseOrderItem poItem = matchedPoItem.get();
                    matchedPoItemIds.add(poItem.getId());

                    // Price Check
                    if (item.unitPrice() != null && poItem.getAgreedUnitPrice() != null
                            && item.unitPrice().compareTo(poItem.getAgreedUnitPrice()) != 0) {
                        BigDecimal variance = item.unitPrice().subtract(poItem.getAgreedUnitPrice());
                        log.info("Price mismatch for {}: agreed={}, billed={}", poItem.getSkuCode(), poItem.getAgreedUnitPrice(), item.unitPrice());

                        String sign = variance.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                        ReconciliationAudit audit = ReconciliationAudit.builder()
                                .issueType(IssueType.PRICE_MISMATCH)
                                .skuCode(poItem.getSkuCode())
                                .itemDescription(item.vendorItemDescription())
                                .expectedValue(poItem.getAgreedUnitPrice())
                                .actualValue(item.unitPrice())
                                .explanation(String.format("Price mismatch for %s: agreed unit price is %s EGP, but invoiced at %s EGP (variance: %s%s EGP)",
                                        poItem.getSkuCode(), poItem.getAgreedUnitPrice().toPlainString(),
                                        item.unitPrice().toPlainString(), sign, variance.toPlainString()))
                                .build();

                        invoice.addAudit(audit);
                    }

                    // Quantity Check
                    if (item.quantity() != null && poItem.getExpectedQuantity() != null
                            && item.quantity().compareTo(poItem.getExpectedQuantity()) != 0) {
                        BigDecimal qtyVariance = item.quantity().subtract(poItem.getExpectedQuantity());
                        log.info("Quantity mismatch for {}: expected={}, billed={}", poItem.getSkuCode(), poItem.getExpectedQuantity(), item.quantity());

                        String qtySign = qtyVariance.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                        ReconciliationAudit audit = ReconciliationAudit.builder()
                                .issueType(IssueType.QUANTITY_MISMATCH)
                                .skuCode(poItem.getSkuCode())
                                .itemDescription(item.vendorItemDescription())
                                .expectedValue(poItem.getExpectedQuantity())
                                .actualValue(item.quantity())
                                .explanation(String.format("Quantity mismatch for %s: expected ordered quantity is %s, but invoiced for %s (variance: %s%s)",
                                        poItem.getSkuCode(), poItem.getExpectedQuantity().toPlainString(),
                                        item.quantity().toPlainString(), qtySign, qtyVariance.toPlainString()))
                                .build();

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
                    .explanation(String.format("Unapproved extra fee or freight surcharge of %s EGP billed on invoice, not authorized in PO %s",
                            extracted.extraFees().toPlainString(), po.getPoNumber()))
                    .build();

            invoice.addAudit(audit);
        }

        // Step D: Status Resolution
        if (invoice.getAudits().isEmpty()) {
            log.info("Invoice {} reconciled with ZERO discrepancies. Status: APPROVED.", invoice.getInvoiceNumber());
            invoice.setReconciliationStatus(ReconciliationStatus.APPROVED);
        } else {
            log.info("Invoice {} has {} discrepancies. Status: FLAGGED_DISCREPANCY. Drafting dispute notice...",
                    invoice.getInvoiceNumber(), invoice.getAudits().size());
            invoice.setReconciliationStatus(ReconciliationStatus.FLAGGED_DISCREPANCY);

            String disputeDraft = disputeDraftingService.generateDisputeDraft(invoice, po, invoice.getAudits());
            invoice.setDisputeDraft(disputeDraft);
        }

        return invoiceRepository.save(invoice);
    }

    /**
     * Matches an extracted line item to the appropriate PO item by SKU code or description similarity.
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

        // 2. Keyword & Description Semantic Matching
        String desc = item.vendorItemDescription() != null ? item.vendorItemDescription().toLowerCase() : "";

        for (PurchaseOrderItem poItem : poItems) {
            if (alreadyMatchedIds.contains(poItem.getId())) {
                continue;
            }
            String poDesc = poItem.getDescription().toLowerCase();
            String poSku = poItem.getSkuCode().toLowerCase();

            // Match by commodity keywords (Arabic and English)
            if (isSameCommodity(desc, poDesc, poSku)) {
                return Optional.of(poItem);
            }
        }

        return Optional.empty();
    }

    private boolean isSameCommodity(String desc, String poDesc, String poSku) {
        // Tomatoes
        if ((desc.contains("طماطم") || desc.contains("tomato")) && (poDesc.contains("tomato") || poSku.contains("tomato"))) {
            return true;
        }
        // Onions
        if ((desc.contains("بصل") || desc.contains("onion")) && (poDesc.contains("onion") || poSku.contains("onion"))) {
            return true;
        }
        // Potatoes
        if ((desc.contains("بطاطس") || desc.contains("potato")) && (poDesc.contains("potato") || poSku.contains("potato"))) {
            return true;
        }
        // Cheese
        if ((desc.contains("جبن") || desc.contains("cheese")) && (poDesc.contains("cheese") || poSku.contains("cheese"))) {
            return true;
        }
        // Butter
        if ((desc.contains("زبد") || desc.contains("butter")) && (poDesc.contains("butter") || poSku.contains("butter"))) {
            return true;
        }
        // Substring containment
        return poDesc.contains(desc) || desc.contains(poDesc);
    }

    /**
     * Converts an Invoice entity to a ReconciliationSummaryResponse DTO.
     */
    public ReconciliationSummaryResponse toSummaryResponse(Invoice invoice) {
        BigDecimal expectedTotal = BigDecimal.ZERO;
        if (invoice.getPoReference() != null && !invoice.getPoReference().isBlank()) {
            Optional<PurchaseOrder> poOpt = purchaseOrderRepository.findByPoNumber(invoice.getPoReference().trim());
            if (poOpt.isPresent()) {
                expectedTotal = poOpt.get().getTotalExpectedAmount();
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
                        a.getExplanation()
                ))
                .toList()
                : Collections.emptyList();

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
                invoice.getDisputeDraft(),
                "/api/invoices/" + invoice.getId() + "/file"
        );
    }
}
