package com.agent.reconciliation.util;

import com.agent.reconciliation.domain.entity.IssueType;
import com.agent.reconciliation.domain.entity.ReconciliationAudit;

import java.math.BigDecimal;

/**
 * Utility helper generating clear, formal audit descriptions and reasons in both Arabic and English.
 */
public class DisputeReasonHelper {

    private DisputeReasonHelper() {}

    public static String getArabicIssueTitle(IssueType issueType) {
        if (issueType == null) return "فارق تدقيق";
        return switch (issueType) {
            case PRICE_MISMATCH -> "اختلاف في سعر الوحدة";
            case QUANTITY_MISMATCH -> "اختلاف في الكمية المورّدة";
            case EXTRA_FEE -> "رسوم إضافية غير معتمدة (مشال/شحن)";
            case UNRECOGNIZED_ITEM -> "بند غير وارد بأمر التوريد";
            case PO_NOT_FOUND -> "أمر التوريد غير مسجل";
        };
    }

    public static String getEnglishIssueTitle(IssueType issueType) {
        if (issueType == null) return "Audit Discrepancy";
        return switch (issueType) {
            case PRICE_MISMATCH -> "Unit Price Mismatch";
            case QUANTITY_MISMATCH -> "Quantity Mismatch";
            case EXTRA_FEE -> "Unapproved Surcharge / Fee";
            case UNRECOGNIZED_ITEM -> "Unrecognized Line Item";
            case PO_NOT_FOUND -> "PO Reference Missing";
        };
    }

    public static String cleanArabicItemDescription(String desc) {
        if (desc == null || desc.isBlank()) return "الصنف المحدد";
        String trimmed = desc.trim();
        if (trimmed.contains("Tomatoes") || trimmed.contains("طماطم")) {
            return "طماطم بلدي طازجة فاخرة";
        }
        if (trimmed.contains("Onions") || trimmed.contains("بصل")) {
            return "بصل أحمر بلدي درجة أولى";
        }
        if (trimmed.toLowerCase().contains("surcharge") || trimmed.contains("مشال") || trimmed.contains("توصيل")) {
            return "رسوم مشال ونقل وتوصيل إضافية";
        }
        // If it has Arabic text before parenthesis, extract it
        if (trimmed.contains("(") && trimmed.contains(")")) {
            int openIdx = trimmed.indexOf('(');
            String before = trimmed.substring(0, openIdx).trim();
            if (before.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.ARABIC)) {
                return before;
            }
            int closeIdx = trimmed.indexOf(')');
            String inside = trimmed.substring(openIdx + 1, closeIdx).trim();
            if (inside.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.ARABIC)) {
                return inside;
            }
        }
        return trimmed;
    }

    public static String cleanEnglishItemDescription(String desc) {
        if (desc == null || desc.isBlank()) return "Specified Line Item";
        String trimmed = desc.trim();
        if (trimmed.contains("Tomatoes") || trimmed.contains("طماطم")) {
            return "Fresh Premium Local Tomatoes";
        }
        if (trimmed.contains("Onions") || trimmed.contains("بصل")) {
            return "Fresh Grade A Red Onions";
        }
        if (trimmed.toLowerCase().contains("surcharge") || trimmed.contains("مشال") || trimmed.contains("توصيل")) {
            return "Express Freight & Delivery Surcharge";
        }
        // If it has English text in parentheses, extract it
        if (trimmed.contains("(") && trimmed.contains(")")) {
            int openIdx = trimmed.indexOf('(');
            int closeIdx = trimmed.indexOf(')');
            String inside = trimmed.substring(openIdx + 1, closeIdx).trim();
            if (inside.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN)) {
                return inside;
            }
            String before = trimmed.substring(0, openIdx).trim();
            if (before.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN)) {
                return before;
            }
        }
        return trimmed;
    }

    public static String cleanArabicVendorName(String vendor) {
        if (vendor == null || vendor.isBlank()) return "السادة المورد المحترمون";
        if (vendor.contains("مزارع الوادي")) return "شركة مزارع الوادي للتوريدات الزراعية";
        return vendor.replaceAll("[a-zA-Z()\\-]", "").trim();
    }

    public static String cleanEnglishVendorName(String vendor) {
        if (vendor == null || vendor.isBlank()) return "Distinguished Vendor Management";
        if (vendor.contains("Al-Wadi")) return "Al-Wadi Commercial Farms Ltd.";
        return vendor.replaceAll("[\\u0600-\\u06FF()\\-]", "").trim();
    }

    public static String getArabicReason(ReconciliationAudit audit) {
        if (audit == null || audit.getIssueType() == null) return "";
        BigDecimal expected = audit.getExpectedValue();
        BigDecimal actual = audit.getActualValue();
        String item = cleanArabicItemDescription(audit.getItemDescription());

        return switch (audit.getIssueType()) {
            case PRICE_MISMATCH -> {
                BigDecimal diff = (actual != null && expected != null) ? actual.subtract(expected) : BigDecimal.ZERO;
                String sign = diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                yield String.format("سعر الوحدة المتفق عليه بأمر التوريد لصنف (%s) هو %s ج.م، بينما تم احتسابه بالفاتورة بسعر %s ج.م (فارق زيادة: %s%s ج.م لكل وحدة).",
                        item,
                        expected != null ? expected.toPlainString() : "0.00",
                        actual != null ? actual.toPlainString() : "0.00",
                        sign, diff.toPlainString());
            }
            case QUANTITY_MISMATCH -> {
                BigDecimal diff = (actual != null && expected != null) ? actual.subtract(expected) : BigDecimal.ZERO;
                String sign = diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                yield String.format("الكمية المطلوبة والمعتمدة بأمر التوريد لصنف (%s) هي %s، بينما ورد بالفاتورة كمية %s (فارق: %s%s).",
                        item,
                        expected != null ? expected.toPlainString() : "0",
                        actual != null ? actual.toPlainString() : "0",
                        sign, diff.toPlainString());
            }
            case EXTRA_FEE -> String.format("تمت إضافة رسوم شحن أو مشال وتوصيل بقيمة %s ج.م دون أي تفويض مسبق أو تغطية تعاقدية بأمر التوريد المعتمد.",
                    actual != null ? actual.toPlainString() : "0.00");
            case UNRECOGNIZED_ITEM -> String.format("الصنف (%s) لم يتم التعاقد عليه أو إدراجه ضمن بنود أمر التوريد الصادر للمورد.", item);
            case PO_NOT_FOUND -> "رقم أمر التوريد المرفق بالفاتورة غير مسجل بقاعدة بيانات المشتريات المعتمدة أو لم يتم تحديده.";
        };
    }

    public static String getEnglishReason(ReconciliationAudit audit) {
        if (audit == null || audit.getIssueType() == null) return "";
        BigDecimal expected = audit.getExpectedValue();
        BigDecimal actual = audit.getActualValue();
        String item = cleanEnglishItemDescription(audit.getItemDescription());

        return switch (audit.getIssueType()) {
            case PRICE_MISMATCH -> {
                BigDecimal diff = (actual != null && expected != null) ? actual.subtract(expected) : BigDecimal.ZERO;
                String sign = diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                yield String.format("Contracted unit rate in PO for '%s' is %s EGP, but billed on invoice at %s EGP (unauthorized variance: %s%s EGP per unit).",
                        item,
                        expected != null ? expected.toPlainString() : "0.00",
                        actual != null ? actual.toPlainString() : "0.00",
                        sign, diff.toPlainString());
            }
            case QUANTITY_MISMATCH -> {
                BigDecimal diff = (actual != null && expected != null) ? actual.subtract(expected) : BigDecimal.ZERO;
                String sign = diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                yield String.format("Ordered quantity in PO for '%s' is %s, but billed for %s (variance: %s%s units).",
                        item,
                        expected != null ? expected.toPlainString() : "0",
                        actual != null ? actual.toPlainString() : "0",
                        sign, diff.toPlainString());
            }
            case EXTRA_FEE -> String.format("Unauthorized surcharge: unapproved freight/delivery fee of %s EGP billed on invoice without contractual authorization in PO.",
                    actual != null ? actual.toPlainString() : "0.00");
            case UNRECOGNIZED_ITEM -> String.format("Unrecognized line item: '%s' does not match any approved product line in the purchase order.", item);
            case PO_NOT_FOUND -> "Purchase order reference was missing or not found in the commercial database.";
        };
    }

    /**
     * Combines Arabic and English reasons into a single coherent explanation.
     */
    public static String getBilingualExplanation(ReconciliationAudit audit) {
        String ar = getArabicReason(audit);
        String en = getEnglishReason(audit);
        if (ar.isBlank()) return en;
        if (en.isBlank()) return ar;
        return ar + " — " + en;
    }

    public static String getBilingualReason(ReconciliationAudit audit) {
        return getBilingualExplanation(audit);
    }
}
