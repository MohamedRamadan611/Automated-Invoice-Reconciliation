package com.agent.reconciliation.service;

import com.agent.reconciliation.domain.dto.ExtractedInvoice;
import com.agent.reconciliation.domain.dto.ExtractedLineItem;
import com.agent.reconciliation.domain.entity.Invoice;
import com.agent.reconciliation.domain.entity.IssueType;
import com.agent.reconciliation.domain.entity.PurchaseOrder;
import com.agent.reconciliation.domain.entity.ReconciliationAudit;
import com.agent.reconciliation.repository.PurchaseOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Service generating formal, corporate-branded commercial Debit Note (إشعار مدين / خصم)
 * PDF documents using Apache PDFBox 3.0.
 * Deducts unapproved variances and calculates the authorized Net Payable Balance.
 */
@Service
public class DebitNoteGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(DebitNoteGeneratorService.class);
    private static final String CURRENCY = "EGP";

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ObjectMapper objectMapper;

    public DebitNoteGeneratorService(PurchaseOrderRepository purchaseOrderRepository,
                                     ObjectMapper objectMapper) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Generates a complete Debit Note PDF as a byte array for the given invoice.
     */
    public byte[] generateDebitNotePdf(Invoice invoice) throws IOException {
        log.info("Generating Debit Note PDF for Invoice: {}", invoice.getInvoiceNumber());

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float margin = 50f;

            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fontOblique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            ExtractedInvoice extracted = parseExtractedInvoice(invoice.getRawJsonPayload());
            List<ReconciliationAudit> audits = invoice.getAudits() != null ? invoice.getAudits() : Collections.emptyList();

            // Calculate deductions
            BigDecimal totalDeduction = BigDecimal.ZERO;
            for (ReconciliationAudit audit : audits) {
                totalDeduction = totalDeduction.add(calculateDeduction(audit, extracted));
            }
            totalDeduction = totalDeduction.setScale(2, RoundingMode.HALF_UP);

            BigDecimal invoicedTotal = invoice.getInvoicedTotal() != null ? invoice.getInvoicedTotal() : BigDecimal.ZERO;
            BigDecimal netPayable = invoicedTotal.subtract(totalDeduction);
            if (netPayable.compareTo(BigDecimal.ZERO) < 0) {
                netPayable = BigDecimal.ZERO;
            }
            netPayable = netPayable.setScale(2, RoundingMode.HALF_UP);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float y = pageHeight - margin;

                // 1. Top Decorative Bar
                cs.setNonStrokingColor(30 / 255f, 41 / 255f, 59 / 255f); // Slate-800
                cs.addRect(margin, y - 8, pageWidth - 2 * margin, 6);
                cs.fill();

                y -= 30;

                // 2. Document Title
                cs.setNonStrokingColor(15 / 255f, 23 / 255f, 42 / 255f); // Slate-900
                cs.beginText();
                cs.setFont(fontBold, 20);
                cs.newLineAtOffset(margin, y);
                cs.showText("COMMERCIAL DEBIT NOTE");
                cs.endText();

                cs.beginText();
                cs.setFont(fontBold, 11);
                cs.setNonStrokingColor(79 / 255f, 70 / 255f, 229 / 255f); // Indigo-600
                cs.newLineAtOffset(pageWidth - margin - 190, y + 4);
                cs.showText("ESH'AR MADIN (DEDUCTION)");
                cs.endText();

                y -= 16;
                cs.beginText();
                cs.setFont(fontRegular, 9);
                cs.setNonStrokingColor(100 / 255f, 116 / 255f, 139 / 255f); // Slate-500
                cs.newLineAtOffset(margin, y);
                cs.showText("Autonomous Accounts Payable Audit & Dispute Settlement Engine");
                cs.endText();

                y -= 25;

                // 3. Metadata Section Box
                cs.setNonStrokingColor(248 / 255f, 250 / 255f, 252 / 255f); // Slate-50
                cs.addRect(margin, y - 65, pageWidth - 2 * margin, 75);
                cs.fill();

                cs.setStrokingColor(226 / 255f, 232 / 255f, 240 / 255f); // Slate-200
                cs.addRect(margin, y - 65, pageWidth - 2 * margin, 75);
                cs.stroke();

                float col1 = margin + 15;
                float col2 = margin + 270;
                float metaY = y - 5;

                cs.setNonStrokingColor(15 / 255f, 23 / 255f, 42 / 255f);
                drawMetaField(cs, fontBold, fontRegular, "Debit Note Ref:", "DN-" + sanitize(invoice.getInvoiceNumber()), col1, metaY);
                drawMetaField(cs, fontBold, fontRegular, "Issue Date:", LocalDate.now().format(DateTimeFormatter.ISO_DATE), col2, metaY);

                metaY -= 16;
                drawMetaField(cs, fontBold, fontRegular, "Original Invoice #:", sanitize(invoice.getInvoiceNumber()), col1, metaY);
                drawMetaField(cs, fontBold, fontRegular, "Supplier (Vendor):", sanitize(invoice.getVendorName()), col2, metaY);

                metaY -= 16;
                drawMetaField(cs, fontBold, fontRegular, "Purchase Order Ref:", sanitize(invoice.getPoReference() != null ? invoice.getPoReference() : "N/A"), col1, metaY);
                drawMetaField(cs, fontBold, fontRegular, "Audit Settlement:", "VARIANCE DEDUCTED", col2, metaY);

                y -= 85;

                // 4. Itemized Deductions Header
                cs.beginText();
                cs.setFont(fontBold, 12);
                cs.setNonStrokingColor(15 / 255f, 23 / 255f, 42 / 255f);
                cs.newLineAtOffset(margin, y);
                cs.showText("Itemized Discrepancy Findings & Authorized Deductions");
                cs.endText();

                y -= 16;

                // Table Header Bar
                cs.setNonStrokingColor(241 / 255f, 245 / 255f, 249 / 255f); // Slate-100
                cs.addRect(margin, y - 5, pageWidth - 2 * margin, 18);
                cs.fill();

                cs.setStrokingColor(203 / 255f, 213 / 255f, 225 / 255f);
                cs.addRect(margin, y - 5, pageWidth - 2 * margin, 18);
                cs.stroke();

                cs.beginText();
                cs.setFont(fontBold, 9);
                cs.setNonStrokingColor(51 / 255f, 65 / 255f, 85 / 255f);
                cs.newLineAtOffset(margin + 8, y);
                cs.showText("Item Description");
                cs.newLineAtOffset(180, 0);
                cs.showText("Issue Type");
                cs.newLineAtOffset(105, 0);
                cs.showText("Billed");
                cs.newLineAtOffset(75, 0);
                cs.showText("PO Agreed");
                cs.newLineAtOffset(70, 0);
                cs.showText("Deduction");
                cs.endText();

                y -= 18;

                // Table Rows
                cs.setFont(fontRegular, 8.5f);
                if (audits.isEmpty()) {
                    cs.beginText();
                    cs.setNonStrokingColor(100 / 255f, 116 / 255f, 139 / 255f);
                    cs.newLineAtOffset(margin + 8, y - 4);
                    cs.showText("No discrepancies recorded. Full invoice matches purchase order terms.");
                    cs.endText();
                    y -= 20;
                } else {
                    for (ReconciliationAudit audit : audits) {
                        BigDecimal lineDeduction = calculateDeduction(audit, extracted);

                        cs.setNonStrokingColor(30 / 255f, 41 / 255f, 59 / 255f);
                        cs.beginText();
                        cs.newLineAtOffset(margin + 8, y - 2);
                        cs.showText(truncate(sanitize(audit.getItemDescription()), 32));

                        cs.newLineAtOffset(180, 0);
                        cs.showText(audit.getIssueType() != null ? audit.getIssueType().name() : "VARIANCE");

                        cs.newLineAtOffset(105, 0);
                        cs.showText(formatNumber(audit.getActualValue()));

                        cs.newLineAtOffset(75, 0);
                        cs.showText(formatNumber(audit.getExpectedValue()));

                        cs.newLineAtOffset(70, 0);
                        cs.showText(formatNumber(lineDeduction) + " EGP");
                        cs.endText();

                        // Row bottom border
                        cs.setStrokingColor(241 / 255f, 245 / 255f, 249 / 255f);
                        cs.moveTo(margin, y - 7);
                        cs.lineTo(pageWidth - margin, y - 7);
                        cs.stroke();

                        y -= 18;
                    }
                }

                y -= 15;

                // 5. Financial Settlement Summary Box
                float summaryBoxWidth = 240f;
                float summaryBoxX = pageWidth - margin - summaryBoxWidth;
                float summaryBoxHeight = 85f;

                cs.setNonStrokingColor(248 / 255f, 250 / 255f, 252 / 255f);
                cs.addRect(summaryBoxX, y - summaryBoxHeight + 10, summaryBoxWidth, summaryBoxHeight);
                cs.fill();

                cs.setStrokingColor(203 / 255f, 213 / 255f, 225 / 255f);
                cs.addRect(summaryBoxX, y - summaryBoxHeight + 10, summaryBoxWidth, summaryBoxHeight);
                cs.stroke();

                float sumY = y;
                drawSummaryRow(cs, fontRegular, fontBold, "Original Invoiced Total:", formatNumber(invoicedTotal) + " EGP", summaryBoxX + 10, sumY, false);
                sumY -= 18;
                drawSummaryRow(cs, fontRegular, fontBold, "Total Debit Note Deduction:", "-" + formatNumber(totalDeduction) + " EGP", summaryBoxX + 10, sumY, true);
                sumY -= 6;

                // Divider line
                cs.setStrokingColor(148 / 255f, 163 / 255f, 184 / 255f);
                cs.moveTo(summaryBoxX + 10, sumY);
                cs.lineTo(summaryBoxX + summaryBoxWidth - 10, sumY);
                cs.stroke();

                sumY -= 16;
                drawSummaryRow(cs, fontBold, fontBold, "NET APPROVED FOR PAYMENT:", formatNumber(netPayable) + " EGP", summaryBoxX + 10, sumY, false);

                y -= 105;

                // 6. Legal & Accounting Certification Clause
                cs.setNonStrokingColor(241 / 255f, 245 / 255f, 249 / 255f);
                cs.addRect(margin, y - 40, pageWidth - 2 * margin, 48);
                cs.fill();

                cs.setStrokingColor(226 / 255f, 232 / 255f, 240 / 255f);
                cs.addRect(margin, y - 40, pageWidth - 2 * margin, 48);
                cs.stroke();

                cs.beginText();
                cs.setFont(fontBold, 8.5f);
                cs.setNonStrokingColor(30 / 255f, 41 / 255f, 59 / 255f);
                cs.newLineAtOffset(margin + 10, y - 5);
                cs.showText("LEGAL ACCOUNTING NOTICE / BIAN QANOONY WA MOHASABY:");
                cs.endText();

                cs.beginText();
                cs.setFont(fontRegular, 8f);
                cs.setNonStrokingColor(71 / 255f, 85 / 255f, 105 / 255f);
                cs.newLineAtOffset(margin + 10, y - 18);
                cs.showText("This official Debit Note constitutes an enforceable commercial deduction resulting from discrepancy audits.");
                cs.newLineAtOffset(0, -11);
                cs.showText("Disbursement shall be executed strictly for the Net Approved Balance pursuant to Purchase Order agreed terms.");
                cs.endText();

                y -= 75;

                // 7. Signatures Area
                float sigWidth = (pageWidth - 2 * margin - 30) / 2;

                // Left Signature (Auditor)
                cs.setStrokingColor(148 / 255f, 163 / 255f, 184 / 255f);
                cs.moveTo(margin, y);
                cs.lineTo(margin + sigWidth, y);
                cs.stroke();

                cs.beginText();
                cs.setFont(fontBold, 8.5f);
                cs.setNonStrokingColor(51 / 255f, 65 / 255f, 85 / 255f);
                cs.newLineAtOffset(margin, y - 14);
                cs.showText("Prepared By: Autonomous AP Reconciliation Auditor");
                cs.newLineAtOffset(0, -10);
                cs.setFont(fontOblique, 7.5f);
                cs.showText("System Verified Hash: " + Integer.toHexString(invoice.hashCode()).toUpperCase());
                cs.endText();

                // Right Signature (Finance Controller)
                float rightSigX = margin + sigWidth + 30;
                cs.moveTo(rightSigX, y);
                cs.lineTo(rightSigX + sigWidth, y);
                cs.stroke();

                cs.beginText();
                cs.setFont(fontBold, 8.5f);
                cs.setNonStrokingColor(51 / 255f, 65 / 255f, 85 / 255f);
                cs.newLineAtOffset(rightSigX, y - 14);
                cs.showText("Authorized By: Finance Controller / AP Manager");
                cs.newLineAtOffset(0, -10);
                cs.setFont(fontOblique, 7.5f);
                cs.showText("Commercial Payment Release Authorization");
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private void drawMetaField(PDPageContentStream cs, PDType1Font fontBold, PDType1Font fontRegular,
                               String label, String value, float x, float y) throws IOException {
        cs.beginText();
        cs.setFont(fontBold, 8.5f);
        cs.newLineAtOffset(x, y);
        cs.showText(label);
        cs.setFont(fontRegular, 8.5f);
        cs.newLineAtOffset(110, 0);
        cs.showText(value);
        cs.endText();
    }

    private void drawSummaryRow(PDPageContentStream cs, PDType1Font fontLabel, PDType1Font fontVal,
                                String label, String value, float x, float y, boolean highlight) throws IOException {
        cs.beginText();
        cs.setFont(fontLabel, 8.5f);
        cs.setNonStrokingColor(highlight ? 185 / 255f : 51 / 255f, highlight ? 28 / 255f : 65 / 255f, highlight ? 28 / 255f : 85 / 255f);
        cs.newLineAtOffset(x, y);
        cs.showText(label);
        cs.setFont(fontVal, 8.5f);
        cs.newLineAtOffset(150, 0);
        cs.showText(value);
        cs.endText();
    }

    private BigDecimal calculateDeduction(ReconciliationAudit audit, ExtractedInvoice extracted) {
        if (audit.getIssueType() == null) return BigDecimal.ZERO;

        BigDecimal actual = audit.getActualValue() != null ? audit.getActualValue() : BigDecimal.ZERO;
        BigDecimal expected = audit.getExpectedValue() != null ? audit.getExpectedValue() : BigDecimal.ZERO;

        return switch (audit.getIssueType()) {
            case PRICE_MISMATCH -> {
                BigDecimal unitDelta = actual.subtract(expected);
                if (unitDelta.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal qty = resolveQuantity(audit, extracted);
                    yield unitDelta.multiply(qty).setScale(2, RoundingMode.HALF_UP);
                }
                yield BigDecimal.ZERO;
            }
            case EXTRA_FEE -> {
                BigDecimal fee = actual.subtract(expected);
                yield (fee.compareTo(BigDecimal.ZERO) > 0 ? fee : actual).setScale(2, RoundingMode.HALF_UP);
            }
            case UNRECOGNIZED_ITEM -> actual.setScale(2, RoundingMode.HALF_UP);
            case QUANTITY_MISMATCH -> {
                BigDecimal qtyDelta = actual.subtract(expected);
                if (qtyDelta.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal unitPrice = resolveUnitPrice(audit, extracted);
                    yield qtyDelta.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
                }
                yield BigDecimal.ZERO;
            }
            case PO_NOT_FOUND -> BigDecimal.ZERO;
        };
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

    private ExtractedInvoice parseExtractedInvoice(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return null;
        try {
            return objectMapper.readValue(rawJson, ExtractedInvoice.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String sanitize(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (c >= 32 && c < 127) {
                sb.append(c);
            } else if (c == ' ' || c == '-' || c == '_' || c == '.') {
                sb.append(c);
            }
        }
        String res = sb.toString().trim();
        return res.isEmpty() ? "N/A" : res;
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max - 3) + "..." : text;
    }

    private String formatNumber(BigDecimal number) {
        if (number == null) return "0.00";
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return df.format(number);
    }
}
