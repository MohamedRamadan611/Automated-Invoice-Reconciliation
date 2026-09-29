package com.agent.reconciliation.service;

import com.agent.reconciliation.domain.entity.Invoice;
import com.agent.reconciliation.domain.entity.PurchaseOrder;
import com.agent.reconciliation.domain.entity.ReconciliationAudit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
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

    public DisputeDraftingService(ChatClient.Builder chatClientBuilder) {
        this(chatClientBuilder, "gemini-3.8-flash");
    }

    @Autowired
    public DisputeDraftingService(ChatClient.Builder chatClientBuilder,
                                 @Value("${spring.ai.openai.chat.options.model:gemini-3.8-flash}") String modelName) {
        this.chatClient = chatClientBuilder.build();
        this.modelName = modelName;
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
            return null;
        }

        String auditSummary = audits.stream()
                .map(a -> String.format("- Issue [%s]: %s | Agreed/Expected: %s EGP, Billed/Actual: %s EGP. Details: %s",
                        a.getIssueType(),
                        a.getItemDescription(),
                        a.getExpectedValue() != null ? a.getExpectedValue().toPlainString() : "N/A",
                        a.getActualValue() != null ? a.getActualValue().toPlainString() : "N/A",
                        a.getExplanation()))
                .collect(Collectors.joining("\n"));

        String poRef = po != null ? po.getPoNumber() : (invoice.getPoReference() != null ? invoice.getPoReference() : "N/A");
        BigDecimal expectedTotal = po != null ? po.getTotalExpectedAmount() : BigDecimal.ZERO;

        String prompt = String.format("""
                You are a senior procurement auditor and commercial disputes specialist.
                Compose a formal, polite, and legally sound financial dispute letter regarding discrepancies found during invoice verification.
                
                Invoice Details:
                - Vendor Name: %s
                - Invoice Number: %s
                - Purchase Order Reference: %s
                - Invoiced Total: %s EGP
                - Agreed PO Expected Total: %s EGP
                
                Itemized Discrepancy Findings:
                %s
                
                Required Structure and Output:
                Produce EXACTLY two sections formatted in Markdown:
                
                ### القسم الأول: إشعار الاعتراض المالي الرسمي (اللغة العربية)
                A formal Egyptian/Arabic business letter addressed to the vendor:
                1. Cite the PO reference number and Invoice number.
                2. Itemize each variance (price hikes, quantity differences, and any unapproved freight/porterage 'مشال/توصيل' fees).
                3. Respectfully request a revised invoice or an immediate credit note (إشعار دائن) to resume payment processing.
                
                ### Section 2: Formal Financial Dispute Notice (Business English)
                A professional commercial letter corresponding to the Arabic section with identical itemized findings and payment remedy conditions.
                """,
                invoice.getVendorName(),
                invoice.getInvoiceNumber(),
                poRef,
                invoice.getInvoicedTotal() != null ? invoice.getInvoicedTotal().toPlainString() : "0.00",
                expectedTotal != null ? expectedTotal.toPlainString() : "0.00",
                auditSummary
        );

        try {
            log.info("Requesting dispute draft from model: {} for invoice: {}", modelName, invoice.getInvoiceNumber());
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(modelName)
                    .temperature(0.2)
                    .build();

            String generated = chatClient.prompt()
                    .options(options)
                    .user(prompt)
                    .call()
                    .content();

            if (generated != null && !generated.isBlank()) {
                return generated.trim();
            }
        } catch (Exception ex) {
            log.warn("AI generation failed for dispute draft (falling back to structured bilingual template): {}", ex.getMessage());
        }

        return generateFallbackDisputeTemplate(invoice, po, audits);
    }

    /**
     * Fallback template generator producing a structured bilingual dispute notice if external LLM is offline.
     */
    public String generateFallbackDisputeTemplate(Invoice invoice, PurchaseOrder po, List<ReconciliationAudit> audits) {
        String poRef = po != null ? po.getPoNumber() : (invoice.getPoReference() != null ? invoice.getPoReference() : "N/A");
        BigDecimal expectedTotal = po != null ? po.getTotalExpectedAmount() : BigDecimal.ZERO;

        StringBuilder sb = new StringBuilder();
        sb.append("### القسم الأول: إشعار الاعتراض المالي الرسمي (اللغة العربية)\n\n");
        sb.append(String.format("السادة / %s المحترمون،\n\n", invoice.getVendorName()));
        sb.append(String.format("تحية طيبة وبعد،،،\n\nبناءً على عمليات المراجعة والتدقيق المالي لفاتورتكم رقم (%s) الصادرة بموجب أمر التوريد رقم (%s)، نود إحاطتكم بوجود فروقات مالية ومحاسبية تتطلب المعالجة والتعديل قبل صرف المستحقات:\n\n",
                invoice.getInvoiceNumber(), poRef));

        sb.append("| البند | نوع الخطأ | القيمة المتفق عليها | القيمة الواردة بالفاتورة | التوضيح |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- |\n");
        for (ReconciliationAudit audit : audits) {
            sb.append(String.format("| %s | %s | %s ج.م | %s ج.م | %s |\n",
                    audit.getItemDescription(),
                    translateIssueTypeArabic(audit.getIssueType().name()),
                    audit.getExpectedValue() != null ? audit.getExpectedValue().toPlainString() : "-",
                    audit.getActualValue() != null ? audit.getActualValue().toPlainString() : "-",
                    audit.getExplanation()));
        }

        sb.append(String.format("\n- إجمالي الفاتورة المرفوعة: %s ج.م\n", invoice.getInvoicedTotal()));
        sb.append(String.format("- الإجمالي المعتمد بأمر التوريد: %s ج.م\n\n", expectedTotal));
        sb.append("نرجو من سيادتكم التكرم بإرسال فاتورة معدلة تتطابق مع بنود أمر التوريد، أو إصدار إشعار دائن (Credit Note) بالفارق لتتمكن الإدارة المالية من استكمال إجراءات الدفع.\n\nوتفضلوا بقبول فائق الاحترام والتقدير،،،\nقسم المراجعة والتدقيق المالي\n\n");

        sb.append("---\n\n");
        sb.append("### Section 2: Formal Financial Dispute Notice (Business English)\n\n");
        sb.append(String.format("Dear %s,\n\n", invoice.getVendorName()));
        sb.append(String.format("Following our financial audit review of Invoice #%s referencing Purchase Order #%s, we have identified discrepancies that preclude immediate payment authorization:\n\n",
                invoice.getInvoiceNumber(), poRef));

        sb.append("| Item Description | Issue Type | Agreed / Expected | Billed / Actual | Details |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- |\n");
        for (ReconciliationAudit audit : audits) {
            sb.append(String.format("| %s | %s | %s EGP | %s EGP | %s |\n",
                    audit.getItemDescription(),
                    audit.getIssueType(),
                    audit.getExpectedValue() != null ? audit.getExpectedValue().toPlainString() : "N/A",
                    audit.getActualValue() != null ? audit.getActualValue().toPlainString() : "N/A",
                    audit.getExplanation()));
        }

        sb.append(String.format("\n- Billed Invoice Total: %s EGP\n", invoice.getInvoicedTotal()));
        sb.append(String.format("- Authorized PO Total: %s EGP\n\n", expectedTotal));
        sb.append("Please issue an amended invoice reflecting the agreed purchase order terms or provide a credit note for the delta variance at your earliest convenience to facilitate payment release.\n\nSincerely,\nFinancial Audit & Accounts Payable Team\n");

        return sb.toString();
    }

    private String translateIssueTypeArabic(String issueType) {
        return switch (issueType) {
            case "PRICE_MISMATCH" -> "اختلاف في السعر";
            case "QUANTITY_MISMATCH" -> "اختلاف في الكمية";
            case "EXTRA_FEE" -> "رسوم إضافية غير معتمدة";
            case "UNRECOGNIZED_ITEM" -> "بند غير وارد بأمر التوريد";
            case "PO_NOT_FOUND" -> "أمر التوريد غير موجود";
            default -> issueType;
        };
    }
}
