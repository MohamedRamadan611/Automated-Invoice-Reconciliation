package com.agent.reconciliation.service;

import com.agent.reconciliation.domain.entity.Invoice;
import com.agent.reconciliation.domain.entity.IssueType;
import com.agent.reconciliation.domain.entity.ReconciliationAudit;
import com.agent.reconciliation.util.DisputeReasonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clean architectural presentation service for email templates.
 * 
 * Invariants:
 * 1. ZERO HTML strings, tags, or inline CSS rules in Java source code.
 * 2. Strictly loads modular external template files from classpath:mail/.
 * 3. Replaces tokens ({{token}}) and concatenates rendered row components cleanly.
 */
@Service
public class EmailTemplateService {

    private static final Logger log = LoggerFactory.getLogger(EmailTemplateService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    /**
     * Loads a classpath resource template file and caches its clean string content.
     */
    public String loadTemplate(String templatePath) {
        return templateCache.computeIfAbsent(templatePath, path -> {
            try {
                ClassPathResource resource = new ClassPathResource(path);
                try (InputStream is = resource.getInputStream()) {
                    return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                }
            } catch (IOException ex) {
                log.error("Failed to load email template from classpath: {}", path, ex);
                throw new IllegalStateException("Email template missing or unreadable: " + path, ex);
            }
        });
    }

    /**
     * Renders a single audit row for the vendor dispute or discrepancy report table.
     */
    public String renderAuditRow(ReconciliationAudit audit, String langMode) {
        boolean isAr = "AR".equalsIgnoreCase(langMode);
        boolean isEn = "EN".equalsIgnoreCase(langMode);

        String issueClass = resolveIssueClass(audit.getIssueType());
        String arabicTitle = DisputeReasonHelper.getArabicIssueTitle(audit.getIssueType());
        String englishTitle = DisputeReasonHelper.getEnglishIssueTitle(audit.getIssueType());
        String arabicReason = DisputeReasonHelper.getArabicReason(audit);
        String englishReason = DisputeReasonHelper.getEnglishReason(audit);

        String issueTypeLabel = isAr ? arabicTitle : (isEn ? englishTitle : arabicTitle);
        String issueSubtitle = (!isAr && !isEn) ? englishTitle : "";

        // SKU Badge
        String skuBadge = "";
        if (audit.getSkuCode() != null && !audit.getSkuCode().isBlank()) {
            skuBadge = loadTemplate("mail/components/sku-badge.html")
                    .replace("{{skuCode}}", escapeHtml(audit.getSkuCode()));
        }

        // Delta Badge
        BigDecimal diff = BigDecimal.ZERO;
        if (audit.getActualValue() != null && audit.getExpectedValue() != null) {
            diff = audit.getActualValue().subtract(audit.getExpectedValue());
        }
        String deltaHtml = "";
        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            String sign = diff.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
            String deltaLabel = isEn ? "Variance" : "الفارق";
            deltaHtml = loadTemplate("mail/components/delta-badge.html")
                    .replace("{{deltaLabel}}", deltaLabel)
                    .replace("{{deltaSign}}", sign)
                    .replace("{{deltaValue}}", diff.toPlainString());
        }

        // Price Breakdown
        String priceTemplatePath = isAr ? "mail/components/price-details-ar.html"
                : (isEn ? "mail/components/price-details-en.html" : "mail/components/price-details-both.html");
        String priceDetails = loadTemplate(priceTemplatePath)
                .replace("{{expectedValue}}", audit.getExpectedValue() != null ? audit.getExpectedValue().toPlainString() : "0.00")
                .replace("{{actualValue}}", audit.getActualValue() != null ? audit.getActualValue().toPlainString() : "0.00")
                .replace("{{deltaHtml}}", deltaHtml);

        // Reason Box
        String reasonTemplatePath = isAr ? "mail/components/reason-box-ar.html"
                : (isEn ? "mail/components/reason-box-en.html" : "mail/components/reason-box-both.html");
        String reasonBox = loadTemplate(reasonTemplatePath)
                .replace("{{arabicReason}}", arabicReason)
                .replace("{{englishReason}}", englishReason);

        return loadTemplate("mail/components/audit-row.html")
                .replace("{{issueClass}}", issueClass)
                .replace("{{issueType}}", issueTypeLabel)
                .replace("{{issueSubtitle}}", issueSubtitle)
                .replace("{{itemDescription}}", escapeHtml(audit.getItemDescription()))
                .replace("{{skuBadge}}", skuBadge)
                .replace("{{priceDetails}}", priceDetails)
                .replace("{{reasonBox}}", reasonBox);
    }

    /**
     * Renders a single row for the manager approval override table.
     */
    public String renderApprovalAuditRow(ReconciliationAudit audit) {
        String arReason = DisputeReasonHelper.getArabicReason(audit);
        String enReason = DisputeReasonHelper.getEnglishReason(audit);

        return loadTemplate("mail/components/audit-row-approval.html")
                .replace("{{issueType}}", DisputeReasonHelper.getEnglishIssueTitle(audit.getIssueType()))
                .replace("{{itemDescription}}", escapeHtml(audit.getItemDescription()))
                .replace("{{expectedValue}}", audit.getExpectedValue() != null ? audit.getExpectedValue().toPlainString() : "0.00")
                .replace("{{actualValue}}", audit.getActualValue() != null ? audit.getActualValue().toPlainString() : "0.00")
                .replace("{{arabicReason}}", arReason)
                .replace("{{englishReason}}", enReason);
    }

    /**
     * Renders the complete HTML body for vendor commercial dispute notifications.
     */
    public String renderVendorDisputeHtml(Invoice invoice, BigDecimal expectedTotal, String langMode) {
        boolean isAr = "AR".equalsIgnoreCase(langMode);
        boolean isEn = "EN".equalsIgnoreCase(langMode);

        // Render audit rows
        StringBuilder auditsBuilder = new StringBuilder();
        List<ReconciliationAudit> audits = invoice.getAudits();
        if (audits != null && !audits.isEmpty()) {
            for (ReconciliationAudit audit : audits) {
                auditsBuilder.append(renderAuditRow(audit, langMode));
            }
        } else {
            auditsBuilder.append(loadTemplate("mail/components/empty-audits-row.html"));
        }

        // Render dispute statement body
        BilingualDraftParts draftParts = parseDraftSections(invoice.getDisputeDraft());
        String disputeBodyTemplate = isAr ? "mail/components/draft-card-ar.html"
                : (isEn ? "mail/components/draft-card-en.html" : "mail/components/draft-card-both.html");
        String disputeBody = loadTemplate(disputeBodyTemplate)
                .replace("{{arabicHtml}}", draftParts.arabicHtml())
                .replace("{{englishHtml}}", draftParts.englishHtml());

        // Render action box
        String actionBoxTemplate = isAr ? "mail/components/action-box-ar.html"
                : (isEn ? "mail/components/action-box-en.html" : "mail/components/action-box-both.html");
        String poRef = invoice.getPoReference() != null ? invoice.getPoReference() : "N/A";
        String actionBox = loadTemplate(actionBoxTemplate)
                .replace("{{poReference}}", escapeHtml(poRef));

        return loadTemplate("mail/vendor-dispute.html")
                .replace("{{lang}}", isAr ? "ar" : "en")
                .replace("{{vendorName}}", escapeHtml(invoice.getVendorName()))
                .replace("{{poReference}}", escapeHtml(poRef))
                .replace("{{invoiceNumber}}", escapeHtml(invoice.getInvoiceNumber()))
                .replace("{{invoicedTotal}}", invoice.getInvoicedTotal() != null ? invoice.getInvoicedTotal().toPlainString() : "0.00")
                .replace("{{expectedTotal}}", expectedTotal != null ? expectedTotal.toPlainString() : "0.00")
                .replace("{{auditTimestamp}}", LocalDateTime.now().format(TIMESTAMP_FORMATTER))
                .replace("{{auditRows}}", auditsBuilder.toString())
                .replace("{{disputeBody}}", disputeBody)
                .replace("{{actionBox}}", actionBox);
    }

    /**
     * Renders the complete HTML body for manager payment authorization and sign-off emails.
     */
    public String renderManagerApprovalHtml(Invoice invoice, BigDecimal expectedTotal, String notes) {
        String notesBlock = "";
        if (notes != null && !notes.isBlank()) {
            notesBlock = loadTemplate("mail/components/notes-box.html")
                    .replace("{{notes}}", escapeHtml(notes));
        }

        String auditTableSection = "";
        if (invoice.getAudits() != null && !invoice.getAudits().isEmpty()) {
            StringBuilder rows = new StringBuilder();
            for (ReconciliationAudit audit : invoice.getAudits()) {
                rows.append(renderApprovalAuditRow(audit));
            }
            auditTableSection = loadTemplate("mail/components/approval-audit-table.html")
                    .replace("{{approvalAuditRows}}", rows.toString());
        }

        String poRef = invoice.getPoReference() != null ? invoice.getPoReference() : "N/A";

        return loadTemplate("mail/manager-approval.html")
                .replace("{{invoiceNumber}}", escapeHtml(invoice.getInvoiceNumber()))
                .replace("{{vendorName}}", escapeHtml(invoice.getVendorName()))
                .replace("{{poReference}}", escapeHtml(poRef))
                .replace("{{invoicedTotal}}", invoice.getInvoicedTotal() != null ? invoice.getInvoicedTotal().toPlainString() : "0.00")
                .replace("{{expectedTotal}}", expectedTotal != null ? expectedTotal.toPlainString() : "0.00")
                .replace("{{authorizationTime}}", LocalDateTime.now().format(TIMESTAMP_FORMATTER))
                .replace("{{notesBlock}}", notesBlock)
                .replace("{{auditTableSection}}", auditTableSection)
                .replace("{{invoiceId}}", String.valueOf(invoice.getId()))
                .replace("{{auditTimestamp}}", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
    }

    /**
     * Renders the complete HTML body for finance manager discrepancy alerts with override CTA.
     */
    public String renderManagerAlertHtml(Invoice invoice, BigDecimal expectedTotal, String overrideUrl) {
        StringBuilder auditsBuilder = new StringBuilder();
        List<ReconciliationAudit> audits = invoice.getAudits();
        if (audits != null && !audits.isEmpty()) {
            for (ReconciliationAudit audit : audits) {
                auditsBuilder.append(renderAuditRow(audit, "BOTH"));
            }
        } else {
            auditsBuilder.append(loadTemplate("mail/components/empty-audits-row.html"));
        }

        String poRef = invoice.getPoReference() != null ? invoice.getPoReference() : "N/A";

        return loadTemplate("mail/manager-alert.html")
                .replace("{{invoiceNumber}}", escapeHtml(invoice.getInvoiceNumber()))
                .replace("{{vendorName}}", escapeHtml(invoice.getVendorName()))
                .replace("{{poReference}}", escapeHtml(poRef))
                .replace("{{invoicedTotal}}", invoice.getInvoicedTotal() != null ? invoice.getInvoicedTotal().toPlainString() : "0.00")
                .replace("{{expectedTotal}}", expectedTotal != null ? expectedTotal.toPlainString() : "0.00")
                .replace("{{auditTimestamp}}", LocalDateTime.now().format(TIMESTAMP_FORMATTER))
                .replace("{{auditRows}}", auditsBuilder.toString())
                .replace("{{overrideUrl}}", overrideUrl)
                .replace("{{invoiceId}}", String.valueOf(invoice.getId()));
    }

    private String resolveIssueClass(IssueType issueType) {
        if (issueType == null) return "issue-po-not-found";
        return switch (issueType) {
            case PRICE_MISMATCH -> "issue-price-mismatch";
            case QUANTITY_MISMATCH -> "issue-quantity-mismatch";
            case EXTRA_FEE -> "issue-extra-fee";
            case UNRECOGNIZED_ITEM -> "issue-unrecognized-item";
            case PO_NOT_FOUND -> "issue-po-not-found";
        };
    }

    private record BilingualDraftParts(String arabicHtml, String englishHtml) {}

    private BilingualDraftParts parseDraftSections(String disputeDraft) {
        if (disputeDraft == null || disputeDraft.isBlank()) {
            String emptyAr = loadTemplate("mail/components/markdown-empty.html")
                    .replace("{{text}}", "لا يوجد مسودة إشعار مرفقة.");
            String emptyEn = loadTemplate("mail/components/markdown-empty.html")
                    .replace("{{text}}", "No dispute draft attached.");
            return new BilingualDraftParts(emptyAr, emptyEn);
        }

        String arRaw;
        String enRaw;

        if (disputeDraft.contains("---")) {
            String[] parts = disputeDraft.split("---", 2);
            arRaw = parts[0].trim();
            enRaw = parts[1].trim();
        } else if (disputeDraft.contains("Section 2:")) {
            int idx = disputeDraft.indexOf("Section 2:");
            arRaw = disputeDraft.substring(0, idx).trim();
            enRaw = disputeDraft.substring(idx).trim();
        } else {
            arRaw = disputeDraft;
            enRaw = disputeDraft;
        }

        return new BilingualDraftParts(formatMarkdownToHtml(arRaw), formatMarkdownToHtml(enRaw));
    }

    private String formatMarkdownToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String text = escapeHtml(markdown);

        // Replace headers, bold, italics, bullets, line breaks using external HTML templates
        String h3Tpl = loadTemplate("mail/components/markdown-h3.html");
        String h2Tpl = loadTemplate("mail/components/markdown-h2.html");
        String strongTpl = loadTemplate("mail/components/markdown-strong.html");
        String emTpl = loadTemplate("mail/components/markdown-em.html");
        String bulletTpl = loadTemplate("mail/components/markdown-item.html");
        String spacerTpl = loadTemplate("mail/components/markdown-spacer.html");
        String breakTpl = loadTemplate("mail/components/markdown-break.html");

        text = replaceAllRegex(text, "###\\s*(.*?)(?:\\n|<br/>|$)", h3Tpl, "$1");
        text = replaceAllRegex(text, "##\\s*(.*?)(?:\\n|<br/>|$)", h2Tpl, "$1");
        text = replaceAllRegex(text, "\\*\\*(.*?)\\*\\*", strongTpl, "$1");
        text = replaceAllRegex(text, "\\*(.*?)\\*", emTpl, "$1");
        text = replaceAllRegex(text, "(?m)^\\s*-\\s*(.*?)$", bulletTpl, "$1");
        text = text.replace("\n\n", spacerTpl);
        text = text.replace("\n", breakTpl);

        return text;
    }

    private String replaceAllRegex(String input, String regex, String template, String placeholder) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String matchContent = matcher.group(1);
            String replacement = template.replace("{{text}}", matchContent);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
