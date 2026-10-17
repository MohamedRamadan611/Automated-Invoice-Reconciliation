package com.agent.reconciliation.service;

import com.agent.reconciliation.domain.dto.CategoryDiscrepancyStat;
import com.agent.reconciliation.domain.dto.ExtractedInvoice;
import com.agent.reconciliation.domain.dto.ExtractedLineItem;
import com.agent.reconciliation.domain.dto.FinancialRoiSummaryResponse;
import com.agent.reconciliation.domain.entity.Invoice;
import com.agent.reconciliation.domain.entity.IssueType;
import com.agent.reconciliation.domain.entity.ReconciliationAudit;
import com.agent.reconciliation.domain.entity.ReconciliationStatus;
import com.agent.reconciliation.repository.InvoiceRepository;
import com.agent.reconciliation.repository.ReconciliationAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Service providing deterministic calculation of portfolio ROI metrics,
 * cash leakage prevented (unauthorized charges, price mismatches, unapproved fees),
 * and generating the CFO Executive Brief using Gemini 3.8 Flash with deterministic fallbacks.
 */
@Service
public class FinancialAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(FinancialAnalyticsService.class);
    private static final String DEFAULT_CURRENCY = "EGP";

    private final InvoiceRepository invoiceRepository;
    private final ReconciliationAuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final String modelName;
    private final Resource briefPromptResource;

    public FinancialAnalyticsService(InvoiceRepository invoiceRepository,
                                     ReconciliationAuditRepository auditRepository) {
        this(invoiceRepository, auditRepository, new ObjectMapper(), null, "gemini-3.8-flash",
                new ClassPathResource("prompts/gemini-executive-brief.st"));
    }

    public FinancialAnalyticsService(InvoiceRepository invoiceRepository,
                                     ReconciliationAuditRepository auditRepository,
                                     ChatClient.Builder chatClientBuilder) {
        this(invoiceRepository, auditRepository, new ObjectMapper(), chatClientBuilder, "gemini-3.8-flash",
                new ClassPathResource("prompts/gemini-executive-brief.st"));
    }

    @Autowired
    public FinancialAnalyticsService(InvoiceRepository invoiceRepository,
                                     ReconciliationAuditRepository auditRepository,
                                     ObjectMapper objectMapper,
                                     @Autowired(required = false) ChatClient.Builder chatClientBuilder,
                                     @Value("${spring.ai.openai.chat.options.model:gemini-3.8-flash}") String modelName,
                                     @Value("classpath:prompts/gemini-executive-brief.st") Resource briefPromptResource) {
        this.invoiceRepository = invoiceRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.chatClient = chatClientBuilder != null ? chatClientBuilder.build() : null;
        this.modelName = modelName;
        this.briefPromptResource = briefPromptResource;
    }

    /**
     * Aggregates portfolio audit data into a comprehensive CFO ROI Summary.
     * All calculations are executed deterministically using BigDecimal.
     */
    public FinancialRoiSummaryResponse getRoiSummary() {
        List<Invoice> invoices = invoiceRepository.findAllWithAudits();
        return calculateRoiSummary(invoices);
    }

    /**
     * Deterministically calculates ROI summary from a collection of invoices.
     */
    public FinancialRoiSummaryResponse calculateRoiSummary(List<Invoice> invoices) {
        BigDecimal totalInvoicedVolume = BigDecimal.ZERO;
        BigDecimal priceMismatchLeakage = BigDecimal.ZERO;
        BigDecimal extraFeesBlocked = BigDecimal.ZERO;
        BigDecimal quantityShortageValuePrevented = BigDecimal.ZERO;

        long cleanInvoicesCount = 0;
        long discrepancyInvoicesCount = 0;
        long manualReviewCount = 0;

        Map<IssueType, DiscrepancyAccumulator> categoryMap = new EnumMap<>(IssueType.class);
        for (IssueType type : IssueType.values()) {
            categoryMap.put(type, new DiscrepancyAccumulator());
        }

        for (Invoice invoice : invoices) {
            if (invoice.getInvoicedTotal() != null) {
                totalInvoicedVolume = totalInvoicedVolume.add(invoice.getInvoicedTotal());
            }

            ReconciliationStatus status = invoice.getReconciliationStatus();
            if (status == ReconciliationStatus.APPROVED) {
                cleanInvoicesCount++;
            } else if (status == ReconciliationStatus.FLAGGED_DISCREPANCY || status == ReconciliationStatus.REJECTED) {
                discrepancyInvoicesCount++;
            } else if (status == ReconciliationStatus.MANUAL_REVIEW) {
                manualReviewCount++;
            }

            ExtractedInvoice extracted = parseExtractedInvoice(invoice.getRawJsonPayload());
            List<ReconciliationAudit> audits = invoice.getAudits() != null ? invoice.getAudits() : Collections.emptyList();

            for (ReconciliationAudit audit : audits) {
                IssueType issueType = audit.getIssueType();
                if (issueType == null) continue;

                DiscrepancyAccumulator accumulator = categoryMap.get(issueType);
                accumulator.count++;

                BigDecimal actual = audit.getActualValue() != null ? audit.getActualValue() : BigDecimal.ZERO;
                BigDecimal expected = audit.getExpectedValue() != null ? audit.getExpectedValue() : BigDecimal.ZERO;

                switch (issueType) {
                    case PRICE_MISMATCH -> {
                        BigDecimal unitDelta = actual.subtract(expected);
                        if (unitDelta.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal qty = resolveQuantity(audit, extracted);
                            BigDecimal lineLeakage = unitDelta.multiply(qty).setScale(2, RoundingMode.HALF_UP);
                            priceMismatchLeakage = priceMismatchLeakage.add(lineLeakage);
                            accumulator.totalAmount = accumulator.totalAmount.add(lineLeakage);
                        }
                    }
                    case EXTRA_FEE -> {
                        BigDecimal fee = actual.subtract(expected);
                        if (fee.compareTo(BigDecimal.ZERO) <= 0) {
                            fee = actual;
                        }
                        fee = fee.setScale(2, RoundingMode.HALF_UP);
                        extraFeesBlocked = extraFeesBlocked.add(fee);
                        accumulator.totalAmount = accumulator.totalAmount.add(fee);
                    }
                    case QUANTITY_MISMATCH -> {
                        BigDecimal qtyDiff = actual.subtract(expected);
                        if (qtyDiff.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal unitPrice = resolveUnitPrice(audit, extracted);
                            BigDecimal qtyLeakage = qtyDiff.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
                            quantityShortageValuePrevented = quantityShortageValuePrevented.add(qtyLeakage);
                            accumulator.totalAmount = accumulator.totalAmount.add(qtyLeakage);
                        }
                    }
                    case UNRECOGNIZED_ITEM -> {
                        BigDecimal unapprovedAmount = actual.setScale(2, RoundingMode.HALF_UP);
                        extraFeesBlocked = extraFeesBlocked.add(unapprovedAmount);
                        accumulator.totalAmount = accumulator.totalAmount.add(unapprovedAmount);
                    }
                    case PO_NOT_FOUND -> {
                        BigDecimal unverifiedAmount = actual.setScale(2, RoundingMode.HALF_UP);
                        accumulator.totalAmount = accumulator.totalAmount.add(unverifiedAmount);
                    }
                }
            }
        }

        BigDecimal totalCashLeakageBlocked = priceMismatchLeakage.add(extraFeesBlocked).setScale(2, RoundingMode.HALF_UP);
        long totalInvoicesAudited = invoices.size();
        double accuracyRate = totalInvoicesAudited > 0
                ? ((double) cleanInvoicesCount / (double) totalInvoicesAudited) * 100.0
                : 100.0;
        accuracyRate = Math.round(accuracyRate * 10.0) / 10.0;

        List<CategoryDiscrepancyStat> topOffenses = new ArrayList<>();
        for (Map.Entry<IssueType, DiscrepancyAccumulator> entry : categoryMap.entrySet()) {
            if (entry.getValue().count > 0) {
                topOffenses.add(new CategoryDiscrepancyStat(
                        entry.getKey(),
                        getIssueLabel(entry.getKey()),
                        entry.getValue().count,
                        entry.getValue().totalAmount.setScale(2, RoundingMode.HALF_UP)
                ));
            }
        }
        topOffenses.sort((a, b) -> b.totalAmount().compareTo(a.totalAmount()));

        String topDriver = topOffenses.isEmpty()
                ? "Zero variances (100% Clean Audit)"
                : topOffenses.get(0).label() + " (" + topOffenses.get(0).totalAmount().toPlainString() + " " + DEFAULT_CURRENCY + ")";

        String executiveBrief = generateExecutiveBrief(
                totalInvoicedVolume,
                totalCashLeakageBlocked,
                priceMismatchLeakage,
                extraFeesBlocked,
                quantityShortageValuePrevented,
                totalInvoicesAudited,
                cleanInvoicesCount,
                discrepancyInvoicesCount,
                manualReviewCount,
                accuracyRate,
                topDriver
        );

        return new FinancialRoiSummaryResponse(
                totalInvoicedVolume.setScale(2, RoundingMode.HALF_UP),
                totalCashLeakageBlocked,
                priceMismatchLeakage.setScale(2, RoundingMode.HALF_UP),
                extraFeesBlocked.setScale(2, RoundingMode.HALF_UP),
                quantityShortageValuePrevented.setScale(2, RoundingMode.HALF_UP),
                totalInvoicesAudited,
                cleanInvoicesCount,
                discrepancyInvoicesCount,
                manualReviewCount,
                accuracyRate,
                topOffenses,
                executiveBrief,
                DEFAULT_CURRENCY
        );
    }

    private String generateExecutiveBrief(BigDecimal totalInvoicedVolume,
                                         BigDecimal totalCashLeakageBlocked,
                                         BigDecimal priceMismatchLeakage,
                                         BigDecimal extraFeesBlocked,
                                         BigDecimal quantityShortageValuePrevented,
                                         long totalInvoices,
                                         long cleanInvoices,
                                         long discrepantInvoices,
                                         long manualReviewInvoices,
                                         double accuracyRate,
                                         String topDriver) {
        if (chatClient != null && briefPromptResource != null && briefPromptResource.exists()) {
            try {
                log.info("Requesting CFO Executive Brief from model: {}", modelName);
                String systemPrompt = "You are an executive CFO assistant. Synthesize a professional, punchy 2-sentence English brief and a 2-sentence Arabic translation for supply chain leadership.";
                OpenAiChatOptions options = OpenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(0.3)
                        .build();

                return chatClient.prompt()
                        .system(systemPrompt)
                        .user(u -> u.text(briefPromptResource)
                                .param("totalInvoicedVolume", formatCurrency(totalInvoicedVolume))
                                .param("totalCashLeakageBlocked", formatCurrency(totalCashLeakageBlocked))
                                .param("priceMismatchLeakage", formatCurrency(priceMismatchLeakage))
                                .param("extraFeesBlocked", formatCurrency(extraFeesBlocked))
                                .param("quantityShortageValuePrevented", formatCurrency(quantityShortageValuePrevented))
                                .param("totalInvoices", totalInvoices)
                                .param("cleanInvoices", cleanInvoices)
                                .param("discrepantInvoices", discrepantInvoices)
                                .param("manualReviewInvoices", manualReviewInvoices)
                                .param("accuracyRate", String.format(Locale.US, "%.1f", accuracyRate))
                                .param("topDriver", topDriver)
                                .param("currency", DEFAULT_CURRENCY))
                        .options(options)
                        .call()
                        .content();
            } catch (Exception ex) {
                log.warn("Gemini call for CFO Executive Brief failed: {}. Falling back to deterministic brief.", ex.getMessage());
            }
        }

        return generateFallbackBrief(totalInvoicedVolume, totalCashLeakageBlocked, totalInvoices, accuracyRate, topDriver);
    }

    private String generateFallbackBrief(BigDecimal totalVolume,
                                         BigDecimal totalLeakage,
                                         long totalInvoices,
                                         double accuracyRate,
                                         String topDriver) {
        String volumeFormatted = formatCurrency(totalVolume);
        String leakageFormatted = formatCurrency(totalLeakage);

        return String.format(Locale.US,
                "Our autonomous AP auditor reconciled %d invoices totaling %s %s with a %.1f%% clean match rate. " +
                "Direct cash leakage of %s %s was prevented, primarily driven by %s.\n\n" +
                "قام المدقق المالي الذاتي بمراجعة وتدقيق %d فاتورة بإجمالي %s %s وبمعدل مطابقة نظيفة %.1f%%. " +
                "وقد نجح النظام في حماية %s %s من الهدر المالي غير المصرح به، والذي تركز بشكل رئيسي في %s.",
                totalInvoices, volumeFormatted, DEFAULT_CURRENCY, accuracyRate,
                leakageFormatted, DEFAULT_CURRENCY, topDriver,
                totalInvoices, volumeFormatted, DEFAULT_CURRENCY, accuracyRate,
                leakageFormatted, DEFAULT_CURRENCY, topDriver
        );
    }

    private ExtractedInvoice parseExtractedInvoice(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return null;
        try {
            return objectMapper.readValue(rawJson, ExtractedInvoice.class);
        } catch (Exception ex) {
            log.debug("Could not deserialize rawJsonPayload: {}", ex.getMessage());
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

    private String getIssueLabel(IssueType issueType) {
        return switch (issueType) {
            case PRICE_MISMATCH -> "Price Mismatch (فروق أسعار)";
            case EXTRA_FEE -> "Unapproved Extra Fee (رسوم ومشال غير معتمدة)";
            case QUANTITY_MISMATCH -> "Quantity Variance (فروق كميات)";
            case UNRECOGNIZED_ITEM -> "Unrecognized Item (أصناف غير معتمدة)";
            case PO_NOT_FOUND -> "Missing PO Reference (أمر توريد مفقود)";
        };
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0.00";
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return df.format(amount);
    }

    private static class DiscrepancyAccumulator {
        long count = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
    }
}
