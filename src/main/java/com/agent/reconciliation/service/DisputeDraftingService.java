package com.agent.reconciliation.service;

import com.agent.reconciliation.domain.entity.Invoice;
import com.agent.reconciliation.domain.entity.PurchaseOrder;
import com.agent.reconciliation.domain.entity.ReconciliationAudit;
import com.agent.reconciliation.util.DisputeReasonHelper;
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
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service generating formal bilingual (Modern Standard Arabic & Business English) dispute drafts
 * for invoices flagged with discrepancies.
 */
@Service
public class DisputeDraftingService {

    private static final Logger log = LoggerFactory.getLogger(DisputeDraftingService.class);

    private final ChatClient chatClient;
    private final String modelName;
    private final Resource disputeSystemPromptResource;
    private final Resource disputeUserPromptResource;

    public DisputeDraftingService(ChatClient.Builder chatClientBuilder) {
        this(chatClientBuilder, "gemini-3.8-flash",
                new ClassPathResource("prompts/gemini-dispute-system.st"),
                new ClassPathResource("prompts/gemini-dispute.st"));
    }

    @Autowired
    public DisputeDraftingService(ChatClient.Builder chatClientBuilder,
                                 @Value("${spring.ai.openai.chat.options.model:gemini-3.8-flash}") String modelName,
                                 @Value("classpath:prompts/gemini-dispute-system.st") Resource disputeSystemPromptResource,
                                 @Value("classpath:prompts/gemini-dispute.st") Resource disputeUserPromptResource) {
        this.chatClient = chatClientBuilder.build();
        this.modelName = modelName;
        this.disputeSystemPromptResource = disputeSystemPromptResource;
        this.disputeUserPromptResource = disputeUserPromptResource;
    }

    /**
     * Generates a formal bilingual dispute notice citing PO reference, invoice number, itemized variances,
     * and request for credit note or revised invoice.
     *
     * @param invoice the invoice with flagged discrepancies
     * @param po the associated purchase order (if found)
     * @param audits list of flagged audit records
     * @return generated bilingual dispute text
     */
    public String generateDisputeDraft(Invoice invoice, PurchaseOrder po, List<ReconciliationAudit> audits) {
        if (audits == null || audits.isEmpty()) {
            return "No discrepancy findings recorded. Invoice matches purchase order terms in full.";
        }

        StringBuilder auditSummaryBuilder = new StringBuilder();
        for (ReconciliationAudit a : audits) {
            if (auditSummaryBuilder.length() > 0) {
                auditSummaryBuilder.append("\n\n");
            }
            auditSummaryBuilder.append(String.format("- Issue [%s] on item '%s':\n" +
                            "  * Agreed / Expected: %s EGP (or units)\n" +
                            "  * Invoiced / Billed: %s EGP (or units)\n" +
                            "  * Explanation: %s",
                    a.getIssueType(),
                    a.getItemDescription(),
                    a.getExpectedValue() != null ? a.getExpectedValue().toPlainString() : "N/A",
                    a.getActualValue() != null ? a.getActualValue().toPlainString() : "N/A",
                    DisputeReasonHelper.getBilingualReason(a)));
        }
        String auditSummary = auditSummaryBuilder.toString();

        String poRef = po != null ? po.getPoNumber() : (invoice.getPoReference() != null ? invoice.getPoReference() : "N/A");
        BigDecimal expectedTotal = po != null ? po.getTotalExpectedAmount() : BigDecimal.ZERO;

        try {
            log.info("Requesting dispute draft from model: {} for invoice: {}", modelName, invoice.getInvoiceNumber());
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(modelName)
                    .temperature(0.2)
                    .build();

            String generated = chatClient.prompt()
                    .options(options)
                    .system(s -> s.text(disputeSystemPromptResource))
                    .user(u -> u.text(disputeUserPromptResource)
                            .param("vendorName", invoice.getVendorName() != null ? invoice.getVendorName() : "Valued Vendor")
                            .param("invoiceNumber", invoice.getInvoiceNumber())
                            .param("poReference", poRef)
                            .param("invoicedTotal", invoice.getInvoicedTotal() != null ? invoice.getInvoicedTotal().toPlainString() : "0.00")
                            .param("expectedTotal", expectedTotal != null ? expectedTotal.toPlainString() : "0.00")
                            .param("auditSummary", auditSummary))
                    .call()
                    .content();

            if (generated != null && !generated.isBlank()) {
                return generated.trim();
            }
        } catch (Throwable ex) {
            log.warn("AI generation failed for dispute draft (falling back to structured bilingual template): {}", ex.getMessage());
        }

        return generateFallbackDisputeTemplate(invoice, po, audits);
    }

    /**
     * Fallback template generator producing a structured bilingual dispute notice if external LLM is offline.
     */
    public String generateFallbackDisputeTemplate(Invoice invoice, PurchaseOrder po, List<ReconciliationAudit> audits) {
        String ar = generateDisputeDraftArabic(invoice, po, audits);
        String en = generateDisputeDraftEnglish(invoice, po, audits);
        return ar + "\n\n---\n\n" + en;
    }

    /**
     * Generates a 100% pure Arabic formal corporate dispute notice with zero English mixing.
     */
    public String generateDisputeDraftArabic(Invoice invoice, PurchaseOrder po, List<ReconciliationAudit> audits) {
        String poRef = po != null ? po.getPoNumber() : (invoice.getPoReference() != null ? invoice.getPoReference() : "N/A");
        BigDecimal invoicedTotal = invoice.getInvoicedTotal() != null ? invoice.getInvoicedTotal() : BigDecimal.ZERO;
        BigDecimal expectedTotal = po != null && po.getTotalExpectedAmount() != null ? po.getTotalExpectedAmount() : BigDecimal.ZERO;
        BigDecimal varianceDelta = invoicedTotal.subtract(expectedTotal);
        String vendorName = DisputeReasonHelper.cleanArabicVendorName(invoice.getVendorName());
        String currentDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.forLanguageTag("ar")));
        String refCode = "AUDIT-DISP-" + invoice.getId() + "-" + (System.currentTimeMillis() % 10000);

        StringBuilder sb = new StringBuilder();
        sb.append("### القسم الأول: إشعار الاعتراض المالي والرقابي الرسمي (اللغة العربية)\n\n");
        sb.append(String.format("**الرقم المرجعي:** %s  \n", refCode));
        sb.append(String.format("**التاريخ:** %s  \n", currentDate));
        sb.append(String.format("**إلى:** السادة/ %s  \n", vendorName));
        sb.append("**عناية:** إدارة الحسابات الدائنة والتحصيل المحترمين  \n");
        sb.append(String.format("**الموضوع:** إشعار رسمي بوجود فروقات مالية ومطالبة بتسوية حسابية للفاتورة رقم: **%s** بموجب أمر التوريد: **%s**  \n\n",
                invoice.getInvoiceNumber(), poRef));

        sb.append("تحية طيبة وبعد،،،\n\n");
        sb.append("تهديكم الإدارة المالية وإدارة مراقبة المدفوعات أطيب التحيات، ونعرب عن تقديرنا لتعاونكم التجاري المشترك. ");
        sb.append(String.format("نحيط سيادتكم علماً بأنه في إطار إجراءات المطابقة الثلاثية للمستندات والرقابة المالية الداخلية على الفاتورة رقم (%s) الصادرة من قبلكم، ", invoice.getInvoiceNumber()));
        sb.append(String.format("تبين وجود عدم تطابق مالي ومحاسبي جوهري بين قيم الفاتورة المرفوعة والبنود والشروط المعتمدة تعاقدياً في أمر التوريد رقم (%s).\n\n", poRef));

        sb.append(String.format("وقد بلغت القيمة الإجمالية المطالب بها بالفاتورة **%s ج.م**، في حين أن الإجمالي التعاقدي المعتمد بأمر التوريد يبلغ **%s ج.م**، مما يترتب عليه فارق زيادة غير معتمد قدره **%s ج.م**.\n\n",
                invoicedTotal.toPlainString(), expectedTotal.toPlainString(), varianceDelta.abs().toPlainString()));

        sb.append("**جدول تفنيد الفروقات المرصودة بدقة:**\n\n");
        sb.append("| الصنف | نوع الفارق الرقابي | السعر/الكمية التعاقدية | الوارد بالفاتورة | سبب الاعتراض والتوجيه المحاسبي |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- |\n");

        if (audits != null) {
            for (ReconciliationAudit audit : audits) {
                String itemName = DisputeReasonHelper.cleanArabicItemDescription(audit.getItemDescription());
                String issueTitle = DisputeReasonHelper.getArabicIssueTitle(audit.getIssueType());
                String expVal = audit.getExpectedValue() != null ? audit.getExpectedValue().toPlainString() + " ج.م" : "0.00 ج.م";
                String actVal = audit.getActualValue() != null ? audit.getActualValue().toPlainString() + " ج.م" : "0.00 ج.م";
                String reason = DisputeReasonHelper.getArabicReason(audit);
                sb.append(String.format("| %s | %s | %s | %s | %s |\n", itemName, issueTitle, expVal, actVal, reason));
            }
        }

        sb.append("\n**الأثر المالي والإجراءات التصحيحية الإلزامية:**  \n");
        sb.append("وفقاً لسياسات الحوكمة المالية الصارمة لدينا، يتعذر على قسم المدفوعات استكمال دورة الصرف أو إصدار أمر الدفع للفاتورة المذكورة بحالتها الراهنة. ولتسوية هذا النزاع التجاري والإفراج الفوري عن مستحقاتكم، يرجى التفضل باتخاذ أحد الإجراءين التاليين:\n\n");
        sb.append(String.format("1. إصدار **إشعار دائن (Credit Note)** رسمي بقيمة الفارق الإجمالي البالغة **%s ج.م** لصالح شركتنا ومرتبط برقم الفاتورة %s.\n",
                varianceDelta.abs().toPlainString(), invoice.getInvoiceNumber()));
        sb.append(String.format("2. **أو** إلغاء الفاتورة الحالية وإعادة إصدار فاتورة ضريبية مصححة مطابقة بنسبة 100%% لبنود وأسعار أمر التوريد بقيمة إجمالية قدرها **%s ج.م**.\n\n",
                expectedTotal.toPlainString()));

        sb.append("يرجى موافاتنا بالمستند المصحح في أقرب وقت لتسريع صرف المستحقات.\n\n");
        sb.append("وتفضلوا بقبول فائق الاحترام والتقدير،،،\n\n");
        sb.append("**قسم المراجعة والتدقيق المالي وإدارة الحسابات الدائنة**  \n");
        sb.append("**القاهرة، جمهورية مصر العربية**\n");

        return sb.toString();
    }

    /**
     * Generates a 100% pure English formal corporate dispute notice with zero Arabic mixing.
     */
    public String generateDisputeDraftEnglish(Invoice invoice, PurchaseOrder po, List<ReconciliationAudit> audits) {
        String poRef = po != null ? po.getPoNumber() : (invoice.getPoReference() != null ? invoice.getPoReference() : "N/A");
        BigDecimal invoicedTotal = invoice.getInvoicedTotal() != null ? invoice.getInvoicedTotal() : BigDecimal.ZERO;
        BigDecimal expectedTotal = po != null && po.getTotalExpectedAmount() != null ? po.getTotalExpectedAmount() : BigDecimal.ZERO;
        BigDecimal varianceDelta = invoicedTotal.subtract(expectedTotal);
        String vendorName = DisputeReasonHelper.cleanEnglishVendorName(invoice.getVendorName());
        String currentDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.US));
        String refCode = "AUDIT-DISP-" + invoice.getId() + "-" + (System.currentTimeMillis() % 10000);

        StringBuilder sb = new StringBuilder();
        sb.append("### Section 2: Formal Financial Dispute Notice (Business English)\n\n");
        sb.append(String.format("**Reference:** %s  \n", refCode));
        sb.append(String.format("**Date:** %s  \n", currentDate));
        sb.append(String.format("**To:** %s  \n", vendorName));
        sb.append("**Attention:** Accounts Receivable & Commercial Billing Department  \n");
        sb.append(String.format("**Subject:** Formal Financial Dispute & Variance Reconciliation Notice – Invoice: **%s** / PO: **%s**  \n\n",
                invoice.getInvoiceNumber(), poRef));

        sb.append("Dear Valued Commercial Partner,\n\n");
        sb.append("The Accounts Payable and Financial Audit Department presents its compliments to your management. ");
        sb.append(String.format("Upon performing our standard commercial three-way matching audit on Invoice #%s, ", invoice.getInvoiceNumber()));
        sb.append(String.format("we identified critical material pricing and variance discrepancies against approved Purchase Order #%s.\n\n", poRef));

        sb.append(String.format("The total billed amount under Invoice #%s is **%s EGP**, whereas the contractually agreed amount authorized under PO #%s is **%s EGP**, resulting in an unapproved variance of **%s EGP**.\n\n",
                invoice.getInvoiceNumber(), invoicedTotal.toPlainString(), poRef, expectedTotal.toPlainString(), varianceDelta.abs().toPlainString()));

        sb.append("**Itemized Discrepancy Findings:**\n\n");
        sb.append("| Line Item | Issue Type | Contracted PO Rate | Billed Invoice Rate | Audit Finding & Remedial Guidance |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- |\n");

        if (audits != null) {
            for (ReconciliationAudit audit : audits) {
                String itemName = DisputeReasonHelper.cleanEnglishItemDescription(audit.getItemDescription());
                String issueTitle = DisputeReasonHelper.getEnglishIssueTitle(audit.getIssueType());
                String expVal = audit.getExpectedValue() != null ? audit.getExpectedValue().toPlainString() + " EGP" : "0.00 EGP";
                String actVal = audit.getActualValue() != null ? audit.getActualValue().toPlainString() + " EGP" : "0.00 EGP";
                String reason = DisputeReasonHelper.getEnglishReason(audit);
                sb.append(String.format("| %s | %s | %s | %s | %s |\n", itemName, issueTitle, expVal, actVal, reason));
            }
        }

        sb.append("\n**Required Corrective Financial Actions:**  \n");
        sb.append("Under corporate internal audit controls, accounts payable cannot authorize payment disbursement against invoices exhibiting unauthorized escalations or unapproved surcharges. To facilitate prompt financial clearance, please submit one of the following:\n\n");
        sb.append(String.format("1. Issue a formal **Credit Note** in the amount of **%s EGP**, referencing Invoice #%s and PO #%s.\n",
                varianceDelta.abs().toPlainString(), invoice.getInvoiceNumber(), poRef));
        sb.append(String.format("2. **Or** cancel the current invoice and reissue an amended commercial invoice reflecting the agreed purchase order total of **%s EGP**.\n\n",
                expectedTotal.toPlainString()));

        sb.append("Please provide the corrected financial documentation at your earliest convenience to resume the payment authorization cycle.\n\n");
        sb.append("Sincerely,\n\n");
        sb.append("**Financial Audit & Commercial Accounts Payable Department**  \n");
        sb.append("**Cairo, Arab Republic of Egypt**\n");

        return sb.toString();
    }

    /**
     * Extracts the clean Arabic section from a full bilingual draft, falling back to dynamic Arabic generation.
     */
    public String extractOrGenerateArabic(String fullDraft, Invoice invoice, PurchaseOrder po, List<ReconciliationAudit> audits) {
        if (fullDraft != null && !fullDraft.isBlank()) {
            if (fullDraft.contains("---")) {
                String[] parts = fullDraft.split("---");
                String firstPart = parts[0].trim();
                if (firstPart.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.ARABIC)) {
                    return firstPart;
                }
            } else if (fullDraft.contains("Section 2:")) {
                int idx = fullDraft.indexOf("Section 2:");
                String firstPart = fullDraft.substring(0, idx).trim();
                if (firstPart.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.ARABIC)) {
                    return firstPart;
                }
            }
        }
        return generateDisputeDraftArabic(invoice, po, audits);
    }

    /**
     * Extracts the clean English section from a full bilingual draft, falling back to dynamic English generation.
     */
    public String extractOrGenerateEnglish(String fullDraft, Invoice invoice, PurchaseOrder po, List<ReconciliationAudit> audits) {
        if (fullDraft != null && !fullDraft.isBlank()) {
            if (fullDraft.contains("---")) {
                String[] parts = fullDraft.split("---");
                if (parts.length > 1) {
                    String secondPart = parts[1].trim();
                    if (secondPart.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN)) {
                        return secondPart;
                    }
                }
            } else if (fullDraft.contains("Section 2:")) {
                int idx = fullDraft.indexOf("Section 2:");
                return fullDraft.substring(idx).trim();
            }
        }
        return generateDisputeDraftEnglish(invoice, po, audits);
    }
}
