package com.agent.reconciliation.service;

import com.agent.reconciliation.domain.dto.CategoryDiscrepancyStat;
import com.agent.reconciliation.domain.dto.ExtractedInvoice;
import com.agent.reconciliation.domain.dto.ExtractedLineItem;
import com.agent.reconciliation.domain.dto.VendorScorecardResponse;
import com.agent.reconciliation.domain.entity.Invoice;
import com.agent.reconciliation.domain.entity.IssueType;
import com.agent.reconciliation.domain.entity.ReconciliationAudit;
import com.agent.reconciliation.domain.entity.ReconciliationStatus;
import com.agent.reconciliation.repository.InvoiceRepository;
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
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service aggregating vendor compliance trends, computing automated risk tiers
 * (CERTIFIED_PARTNER, AUDIT_SCRUTINY, HIGH_RISK), and providing AI Risk Narratives.
 */
@Service
public class VendorScorecardService {

    private static final Logger log = LoggerFactory.getLogger(VendorScorecardService.class);

    public static final String TIER_CERTIFIED_PARTNER = "CERTIFIED_PARTNER";
    public static final String TIER_AUDIT_SCRUTINY = "AUDIT_SCRUTINY";
    public static final String TIER_HIGH_RISK = "HIGH_RISK";

    private final InvoiceRepository invoiceRepository;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final String modelName;
    private final Resource riskPromptResource;

    public VendorScorecardService(InvoiceRepository invoiceRepository) {
        this(invoiceRepository, new ObjectMapper(), null, "gemini-3.8-flash",
                new ClassPathResource("prompts/gemini-vendor-risk.st"));
    }

    public VendorScorecardService(InvoiceRepository invoiceRepository,
                                  ChatClient.Builder chatClientBuilder) {
        this(invoiceRepository, new ObjectMapper(), chatClientBuilder, "gemini-3.8-flash",
                new ClassPathResource("prompts/gemini-vendor-risk.st"));
    }

    @Autowired
    public VendorScorecardService(InvoiceRepository invoiceRepository,
                                  ObjectMapper objectMapper,
                                  @Autowired(required = false) ChatClient.Builder chatClientBuilder,
                                  @Value("${spring.ai.openai.chat.options.model:gemini-3.8-flash}") String modelName,
                                  @Value("classpath:prompts/gemini-vendor-risk.st") Resource riskPromptResource) {
        this.invoiceRepository = invoiceRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.chatClient = chatClientBuilder != null ? chatClientBuilder.build() : null;
        this.modelName = modelName;
        this.riskPromptResource = riskPromptResource;
    }

    /**
     * Aggregates all vendor scorecards across the system, ranked by risk and overcharge volume.
     */
    public List<VendorScorecardResponse> getVendorScorecards() {
        List<Invoice> allInvoices = invoiceRepository.findAllWithAudits();
        Map<String, List<Invoice>> grouped = allInvoices.stream()
                .collect(Collectors.groupingBy(
                        inv -> (inv.getVendorName() != null && !inv.getVendorName().isBlank())
                                ? inv.getVendorName().trim()
                                : "Unknown Vendor"
                ));

        List<VendorScorecardResponse> scorecards = new ArrayList<>();
        for (Map.Entry<String, List<Invoice>> entry : grouped.entrySet()) {
            scorecards.add(buildScorecardForVendor(entry.getKey(), entry.getValue()));
        }

        scorecards.sort((a, b) -> {
            int cmp = b.totalOverchargeAttempted().compareTo(a.totalOverchargeAttempted());
            if (cmp != 0) return cmp;
            return Double.compare(b.discrepancyRate(), a.discrepancyRate());
        });

        return scorecards;
    }

    /**
     * Returns scorecard for a specific vendor.
     */
    public Optional<VendorScorecardResponse> getVendorScorecard(String vendorName) {
        if (vendorName == null || vendorName.isBlank()) return Optional.empty();
        List<VendorScorecardResponse> all = getVendorScorecards();
        return all.stream()
                .filter(s -> s.vendorName().equalsIgnoreCase(vendorName.trim()))
                .findFirst();
    }

    /**
     * Deterministically builds a single VendorScorecardResponse from a vendor's invoices.
     */
    public VendorScorecardResponse buildScorecardForVendor(String vendorName, List<Invoice> invoices) {
        int totalInvoices = invoices.size();
        int cleanInvoices = 0;
        int discrepancyInvoices = 0;
        BigDecimal totalBilledAmount = BigDecimal.ZERO;
        BigDecimal totalOverchargeAttempted = BigDecimal.ZERO;
        Instant lastAuditedDate = null;

        Map<IssueType, Long> issueCounts = new EnumMap<>(IssueType.class);
        Map<IssueType, BigDecimal> issueAmounts = new EnumMap<>(IssueType.class);
        for (IssueType type : IssueType.values()) {
            issueCounts.put(type, 0L);
            issueAmounts.put(type, BigDecimal.ZERO);
        }

        for (Invoice invoice : invoices) {
            if (invoice.getInvoicedTotal() != null) {
                totalBilledAmount = totalBilledAmount.add(invoice.getInvoicedTotal());
            }

            if (invoice.getCreatedAt() != null) {
                if (lastAuditedDate == null || invoice.getCreatedAt().isAfter(lastAuditedDate)) {
                    lastAuditedDate = invoice.getCreatedAt();
                }
            }

            ReconciliationStatus status = invoice.getReconciliationStatus();
            if (status == ReconciliationStatus.APPROVED) {
                cleanInvoices++;
            } else if (status == ReconciliationStatus.FLAGGED_DISCREPANCY || status == ReconciliationStatus.REJECTED) {
                discrepancyInvoices++;
            }

            ExtractedInvoice extracted = parseExtractedInvoice(invoice.getRawJsonPayload());
            List<ReconciliationAudit> audits = invoice.getAudits() != null ? invoice.getAudits() : Collections.emptyList();

            for (ReconciliationAudit audit : audits) {
                IssueType type = audit.getIssueType();
                if (type == null) continue;

                issueCounts.put(type, issueCounts.get(type) + 1);

                BigDecimal actual = audit.getActualValue() != null ? audit.getActualValue() : BigDecimal.ZERO;
                BigDecimal expected = audit.getExpectedValue() != null ? audit.getExpectedValue() : BigDecimal.ZERO;
                BigDecimal overcharge = BigDecimal.ZERO;

                switch (type) {
                    case PRICE_MISMATCH -> {
                        BigDecimal delta = actual.subtract(expected);
                        if (delta.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal qty = resolveQuantity(audit, extracted);
                            overcharge = delta.multiply(qty).setScale(2, RoundingMode.HALF_UP);
                        }
                    }
                    case EXTRA_FEE -> {
                        BigDecimal fee = actual.subtract(expected);
                        overcharge = (fee.compareTo(BigDecimal.ZERO) > 0 ? fee : actual).setScale(2, RoundingMode.HALF_UP);
                    }
                    case UNRECOGNIZED_ITEM -> overcharge = actual.setScale(2, RoundingMode.HALF_UP);
                    case QUANTITY_MISMATCH -> {
                        BigDecimal qtyDelta = actual.subtract(expected);
                        if (qtyDelta.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal unitPrice = resolveUnitPrice(audit, extracted);
                            overcharge = qtyDelta.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
                        }
                    }
                    case PO_NOT_FOUND -> overcharge = BigDecimal.ZERO;
                }

                totalOverchargeAttempted = totalOverchargeAttempted.add(overcharge);
                issueAmounts.put(type, issueAmounts.get(type).add(overcharge));
            }
        }

        double cleanMatchRate = totalInvoices > 0
                ? ((double) cleanInvoices / (double) totalInvoices) * 100.0
                : 100.0;
        cleanMatchRate = Math.round(cleanMatchRate * 10.0) / 10.0;

        double discrepancyRate = Math.round((100.0 - cleanMatchRate) * 10.0) / 10.0;

        // Automated Tier Assignment:
        // < 3% -> CERTIFIED_PARTNER
        // 3% to 15% -> AUDIT_SCRUTINY
        // > 15% -> HIGH_RISK
        String tier;
        if (discrepancyRate < 3.0) {
            tier = TIER_CERTIFIED_PARTNER;
        } else if (discrepancyRate <= 15.0) {
            tier = TIER_AUDIT_SCRUTINY;
        } else {
            tier = TIER_HIGH_RISK;
        }

        List<CategoryDiscrepancyStat> topCategories = new ArrayList<>();
        for (IssueType type : IssueType.values()) {
            long count = issueCounts.get(type);
            if (count > 0) {
                topCategories.add(new CategoryDiscrepancyStat(
                        type,
                        getIssueLabel(type),
                        count,
                        issueAmounts.get(type).setScale(2, RoundingMode.HALF_UP)
                ));
            }
        }
        topCategories.sort((a, b) -> b.totalAmount().compareTo(a.totalAmount()));

        String riskNarrative = generateRiskNarrative(
                vendorName,
                tier,
                cleanMatchRate,
                discrepancyRate,
                totalBilledAmount,
                totalOverchargeAttempted,
                totalInvoices,
                cleanInvoices,
                discrepancyInvoices,
                topCategories
        );

        return new VendorScorecardResponse(
                vendorName,
                totalInvoices,
                cleanInvoices,
                discrepancyInvoices,
                cleanMatchRate,
                discrepancyRate,
                totalBilledAmount.setScale(2, RoundingMode.HALF_UP),
                totalOverchargeAttempted.setScale(2, RoundingMode.HALF_UP),
                tier,
                topCategories,
                riskNarrative,
                lastAuditedDate != null ? lastAuditedDate : Instant.now()
        );
    }

    private String generateRiskNarrative(String vendorName,
                                        String tier,
                                        double cleanMatchRate,
                                        double discrepancyRate,
                                        BigDecimal totalBilled,
                                        BigDecimal totalOvercharge,
                                        int totalInvoices,
                                        int cleanInvoices,
                                        int discrepancyInvoices,
                                        List<CategoryDiscrepancyStat> topCategories) {
        String topOffensesStr = topCategories.isEmpty()
                ? "None (Perfect billing compliance)"
                : topCategories.stream().map(c -> c.label() + " (" + c.count() + "x, " + c.totalAmount() + " EGP)").collect(Collectors.joining(", "));

        if (chatClient != null && riskPromptResource != null && riskPromptResource.exists()) {
            try {
                log.info("Requesting Gemini vendor risk narrative for '{}' (Tier: {})", vendorName, tier);
                OpenAiChatOptions options = OpenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(0.3)
                        .build();

                return chatClient.prompt()
                        .system("You are an enterprise procurement risk specialist. Provide a brief 2-sentence risk summary with clear commercial advice.")
                        .user(u -> u.text(riskPromptResource)
                                .param("vendorName", vendorName)
                                .param("tier", tier)
                                .param("cleanMatchRate", String.format(Locale.US, "%.1f", cleanMatchRate))
                                .param("discrepancyRate", String.format(Locale.US, "%.1f", discrepancyRate))
                                .param("totalBilled", formatCurrency(totalBilled))
                                .param("totalOvercharge", formatCurrency(totalOvercharge))
                                .param("totalInvoices", totalInvoices)
                                .param("cleanInvoices", cleanInvoices)
                                .param("discrepantInvoices", discrepancyInvoices)
                                .param("topOffenses", topOffensesStr))
                        .options(options)
                        .call()
                        .content();
            } catch (Exception ex) {
                log.warn("Gemini call for Vendor Risk narrative failed: {}. Using deterministic fallback.", ex.getMessage());
            }
        }

        return generateFallbackRiskNarrative(vendorName, tier, cleanMatchRate, discrepancyRate, totalOvercharge);
    }

    private String generateFallbackRiskNarrative(String vendorName,
                                                 String tier,
                                                 double cleanMatchRate,
                                                 double discrepancyRate,
                                                 BigDecimal totalOvercharge) {
        String overchargeFormatted = formatCurrency(totalOvercharge);

        return switch (tier) {
            case TIER_CERTIFIED_PARTNER -> String.format(Locale.US,
                    "%s demonstrates outstanding billing fidelity with a %.1f%% clean match rate. Low operational risk; recommended for expedited AP clearing. " +
                    "| يظهر المورد التزاماً نموذجياً بمعدل مطابقة %.1f%% ومخاطر منخفضة، ويوصى بصرف مستحقاته عبر المسار السريع.",
                    vendorName, cleanMatchRate, cleanMatchRate);
            case TIER_AUDIT_SCRUTINY -> String.format(Locale.US,
                    "%s exhibits occasional variances with a %.1f%% discrepancy rate, attempting %s EGP in unapproved charges. Recommend mandatory manager approval for price exceptions. " +
                    "| يسجل المورد تذبذباً بنسبة فروقات %.1f%% ومحاولات تحصيل غير معتمدة بقيمة %s ج.م؛ يوصى بتشديد الرقابة والموافقة المسبقة.",
                    vendorName, discrepancyRate, overchargeFormatted, discrepancyRate, overchargeFormatted);
            case TIER_HIGH_RISK -> String.format(Locale.US,
                    "%s represents severe commercial risk with a %.1f%% discrepancy rate and %s EGP in blocked overcharges. Strongly advise auditing past contracts and placing payment terms under strict review. " +
                    "| يشكل المورد خطورة تعاقدية عالية بمعدل عدم مطابقة %.1f%% وحجم هدر محجوب قدره %s ج.م؛ ينصح بإعادة تقييم العقد وتجميد الدفعات التلقائية.",
                    vendorName, discrepancyRate, overchargeFormatted, discrepancyRate, overchargeFormatted);
            default -> "Supplier compliance audit in progress.";
        };
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

    private String getIssueLabel(IssueType issueType) {
        return switch (issueType) {
            case PRICE_MISMATCH -> "Price Mismatch (فروق أسعار)";
            case EXTRA_FEE -> "Extra Fees (رسوم غير معتمدة)";
            case QUANTITY_MISMATCH -> "Quantity Variance (فروق كميات)";
            case UNRECOGNIZED_ITEM -> "Unrecognized Item (أصناف غير معتمدة)";
            case PO_NOT_FOUND -> "Missing PO (أمر توريد مفقود)";
        };
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0.00";
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return df.format(amount);
    }
}
