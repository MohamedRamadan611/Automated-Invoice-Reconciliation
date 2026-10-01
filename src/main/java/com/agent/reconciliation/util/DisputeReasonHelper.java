package com.agent.reconciliation.util;

import com.agent.reconciliation.domain.entity.IssueType;
import com.agent.reconciliation.domain.entity.ReconciliationAudit;

import java.math.BigDecimal;

/**
 * Utility helper generating clear, formal audit descriptions and reasons in both Arabic and English.
 * Completely generic across all commodities and industries; zero hardcoded vendor or item mappings.
 */
public class DisputeReasonHelper {

    private DisputeReasonHelper() {}

    public static String getArabicIssueTitle(IssueType issueType) {
        if (issueType == null) return "فارق تدقيق";
        return switch (issueType) {
            case PRICE_MISMATCH -> "اختلاف في سعر الوحدة";
            case QUANTITY_MISMATCH -> "اختلاف في الكمية أو الوزن المورّد";
            case EXTRA_FEE -> "رسوم إضافية غير معتمدة (مشال/شحن/مصاريف إدارية)";
            case UNRECOGNIZED_ITEM -> "بند غير وارد بأمر التوريد";
            case PO_NOT_FOUND -> "أمر التوريد غير مسجل";
        };
    }

    public static String getEnglishIssueTitle(IssueType issueType) {
        if (issueType == null) return "Audit Discrepancy";
        return switch (issueType) {
            case PRICE_MISMATCH -> "Unit Price Mismatch";
            case QUANTITY_MISMATCH -> "Quantity / Weight Mismatch";
            case EXTRA_FEE -> "Unapproved Surcharge / Logistics Fee";
            case UNRECOGNIZED_ITEM -> "Unrecognized Line Item";
            case PO_NOT_FOUND -> "PO Reference Missing";
        };
    }

    public static String cleanArabicItemDescription(String desc) {
        if (desc == null || desc.isBlank()) return "الصنف المحدد";
        String trimmed = desc.trim();

        // If it has Arabic text inside or outside parentheses, extract the Arabic part
        if (trimmed.contains("(") && trimmed.contains(")")) {
            int openIdx = trimmed.indexOf('(');
            int closeIdx = trimmed.indexOf(')');
            String before = trimmed.substring(0, openIdx).trim();
            if (containsArabic(before)) {
                return before;
            }
            if (closeIdx > openIdx) {
                String inside = trimmed.substring(openIdx + 1, closeIdx).trim();
                if (containsArabic(inside)) {
                    return inside;
                }
            }
        }

        return trimmed;
    }

    public static String cleanEnglishItemDescription(String desc) {
        if (desc == null || desc.isBlank()) return "Specified Line Item";
        String trimmed = desc.trim();

        // If it has English text inside or outside parentheses, extract the English part
        if (trimmed.contains("(") && trimmed.contains(")")) {
            int openIdx = trimmed.indexOf('(');
            int closeIdx = trimmed.indexOf(')');
            if (closeIdx > openIdx) {
                String inside = trimmed.substring(openIdx + 1, closeIdx).trim();
                if (containsLatin(inside)) {
                    return inside;
                }
            }
            String before = trimmed.substring(0, openIdx).trim();
            if (containsLatin(before)) {
                return before;
            }
        }

        return trimmed;
    }

    public static String cleanArabicVendorName(String vendor) {
        if (vendor == null || vendor.isBlank()) return "السادة المورد المحترمون";
        String trimmed = vendor.trim();

        if (trimmed.contains("(") && trimmed.contains(")")) {
            int openIdx = trimmed.indexOf('(');
            int closeIdx = trimmed.indexOf(')');
            if (closeIdx > openIdx) {
                String inside = trimmed.substring(openIdx + 1, closeIdx).trim();
                if (containsArabic(inside)) return inside;
            }
            String before = trimmed.substring(0, openIdx).trim();
            if (containsArabic(before)) return before;
        }

        return trimmed;
    }

    public static String cleanEnglishVendorName(String vendor) {
        if (vendor == null || vendor.isBlank()) return "Distinguished Vendor Management";
        String trimmed = vendor.trim();

        if (trimmed.contains("(") && trimmed.contains(")")) {
            int openIdx = trimmed.indexOf('(');
            String before = trimmed.substring(0, openIdx).trim();
            if (containsLatin(before)) return before;
            int closeIdx = trimmed.indexOf(')');
            if (closeIdx > openIdx) {
                String inside = trimmed.substring(openIdx + 1, closeIdx).trim();
                if (containsLatin(inside)) return inside;
            }
        }

        return trimmed;
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

    private static boolean containsArabic(String s) {
        return s != null && s.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.ARABIC);
    }

    private static boolean containsLatin(String s) {
        return s != null && s.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN);
    }
}
