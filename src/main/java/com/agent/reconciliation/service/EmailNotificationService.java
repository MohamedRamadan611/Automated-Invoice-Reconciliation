package com.agent.reconciliation.service;

import com.agent.reconciliation.config.AppProperties;
import com.agent.reconciliation.domain.entity.Invoice;
import com.agent.reconciliation.domain.entity.PurchaseOrder;
import com.agent.reconciliation.repository.PurchaseOrderRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Clean architectural email notification service:
 * Responsible strictly for dispatching transactional emails via JavaMailSender or logging safely in simulation mode.
 * Contains ZERO HTML tags or inline CSS rules; delegates all presentation rendering to EmailTemplateService.
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    public record EmailDispatchResult(boolean success, boolean simulated, String recipient, String message) {}

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final EmailTemplateService emailTemplateService;
    private final AppProperties appProperties;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public EmailNotificationService(ObjectProvider<JavaMailSender> mailSenderProvider,
                                  PurchaseOrderRepository purchaseOrderRepository,
                                  EmailTemplateService emailTemplateService,
                                  AppProperties appProperties) {
        this.mailSenderProvider = mailSenderProvider;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.emailTemplateService = emailTemplateService;
        this.appProperties = appProperties;
    }

    /**
     * Sends a formal dispute notice to the vendor explaining discrepancies,
     * embedding the Gemini AI dispute draft with reasons, and CC'ing the finance manager.
     */
    public EmailDispatchResult sendDisputeEmailToVendor(Invoice invoice, String targetEmail) {
        return sendDisputeEmailToVendor(invoice, targetEmail, "BOTH");
    }

    /**
     * Sends a formal dispute notice to the vendor in specified language (BOTH, AR, EN).
     */
    public EmailDispatchResult sendDisputeEmailToVendor(Invoice invoice, String targetEmail, String lang) {
        String defaultVendor = appProperties.mail().getEffectiveVendorEmail();
        String defaultManager = appProperties.mail().getEffectiveManagerEmail();

        String recipient = (targetEmail != null && !targetEmail.trim().isEmpty())
                ? targetEmail.trim()
                : defaultVendor;

        String langMode = (lang != null && !lang.isBlank()) ? lang.trim().toUpperCase() : "BOTH";
        String poRef = invoice.getPoReference() != null ? invoice.getPoReference() : "N/A";

        String subject = switch (langMode) {
            case "AR" -> String.format("[اعتراض تجاري] إشعار بفروقات محاسبية بالفاتورة رقم %s (أمر توريد: %s)",
                    invoice.getInvoiceNumber(), poRef);
            case "EN" -> String.format("[COMMERCIAL DISPUTE] Invoice Discrepancy Notice - %s (PO: %s)",
                    invoice.getInvoiceNumber(), poRef);
            default -> String.format("[COMMERCIAL DISPUTE / إشعار اعتراض تجاري] Invoice Discrepancy Notice - %s (PO: %s)",
                    invoice.getInvoiceNumber(), poRef);
        };

        BigDecimal expectedTotal = resolveExpectedTotal(invoice);
        String htmlBody = emailTemplateService.renderVendorDisputeHtml(invoice, expectedTotal, langMode);

        return dispatchEmail(recipient, defaultManager, subject, htmlBody, "VENDOR DISPUTE NOTICE (" + langMode + ")");
    }

    /**
     * Sends a formal payment authorization and responsibility sign-off notification
     * to the finance manager when an invoice is approved.
     */
    public EmailDispatchResult sendApprovalEmailToManager(Invoice invoice, String targetEmail, String notes) {
        String defaultManager = appProperties.mail().getEffectiveManagerEmail();
        String recipient = (targetEmail != null && !targetEmail.trim().isEmpty())
                ? targetEmail.trim()
                : defaultManager;

        String subject = "[PAYMENT AUTHORIZED] Invoice Payment Release Sign-Off - " 
                + invoice.getInvoiceNumber() + " (PO: " + (invoice.getPoReference() != null ? invoice.getPoReference() : "N/A") + ")";

        BigDecimal expectedTotal = resolveExpectedTotal(invoice);
        String htmlBody = emailTemplateService.renderManagerApprovalHtml(invoice, expectedTotal, notes);

        return dispatchEmail(recipient, null, subject, htmlBody, "MANAGER APPROVAL & PAYMENT SIGN-OFF");
    }

    /**
     * Sends a discrepancy warning alert to the finance manager with override CTA link.
     */
    public EmailDispatchResult sendDiscrepancyAlertToManager(Invoice invoice, String targetEmail, String overrideUrl) {
        String defaultManager = appProperties.mail().getEffectiveManagerEmail();
        String recipient = (targetEmail != null && !targetEmail.trim().isEmpty())
                ? targetEmail.trim()
                : defaultManager;

        String poRef = invoice.getPoReference() != null ? invoice.getPoReference() : "N/A";
        String subject = String.format("[FINANCE ALERT] Action Required: Invoice %s Discrepancy (PO: %s)",
                invoice.getInvoiceNumber(), poRef);

        BigDecimal expectedTotal = resolveExpectedTotal(invoice);
        String effectiveUrl = (overrideUrl != null && !overrideUrl.isBlank())
                ? overrideUrl
                : appProperties.mail().getEffectiveFrontendUrl() + "/invoice/" + invoice.getId();

        String htmlBody = emailTemplateService.renderManagerAlertHtml(invoice, expectedTotal, effectiveUrl);

        return dispatchEmail(recipient, null, subject, htmlBody, "MANAGER DISCREPANCY ALERT");
    }

    private BigDecimal resolveExpectedTotal(Invoice invoice) {
        if (invoice.getPoReference() != null && !invoice.getPoReference().isBlank()) {
            Optional<PurchaseOrder> poOpt = purchaseOrderRepository.findByPoNumber(invoice.getPoReference().trim());
            if (poOpt.isPresent()) {
                return poOpt.get().getTotalExpectedAmount();
            }
        }
        return BigDecimal.ZERO;
    }

    private EmailDispatchResult dispatchEmail(String to, String cc, String subject, String htmlContent, String flowType) {
        boolean credentialsConfigured = mailUsername != null && !mailUsername.isBlank()
                && mailPassword != null && !mailPassword.isBlank();

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();

        if (!credentialsConfigured || mailSender == null) {
            logSimulatedEmail(to, cc, subject, flowType, htmlContent);
            return new EmailDispatchResult(
                    true,
                    true,
                    to,
                    "Simulation Mode: " + flowType + " formatted and logged cleanly. (Provide Gmail credentials in .env for live transmission)"
            );
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailUsername, "Automated Invoice Reconciliation System");
            helper.setTo(to);
            if (cc != null && !cc.isBlank() && !cc.equalsIgnoreCase(to)) {
                helper.setCc(cc);
            }
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Successfully sent live email [{}] to {} (cc: {})", flowType, to, cc);
            return new EmailDispatchResult(true, false, to, "Live email successfully delivered to " + to);
        } catch (Exception ex) {
            log.error("Failed to transmit email via SMTP: {}. Falling back to simulation mode.", ex.getMessage());
            logSimulatedEmail(to, cc, subject, flowType, htmlContent);
            return new EmailDispatchResult(
                    true,
                    true,
                    to,
                    "SMTP delivery failed (" + ex.getMessage() + "); safely simulated in server logs."
            );
        }
    }

    private void logSimulatedEmail(String to, String cc, String subject, String flowType, String htmlContent) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        log.info("""
                
                ================================================================================
                [EMAIL NOTIFICATION SERVICE - {} - SIMULATION MODE]
                Timestamp: {}
                To:        {}
                CC:        {}
                Subject:   {}
                --------------------------------------------------------------------------------
                Body Preview (HTML Length: {} chars):
                {}
                ================================================================================
                """,
                flowType,
                timestamp,
                to,
                cc != null ? cc : "[None]",
                subject,
                htmlContent.length(),
                htmlContent.replaceAll("(?s)<style.*?</style>", "").replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim()
        );
    }
}
