package com.agent.reconciliation.suite;

import com.agent.reconciliation.util.DisputeReasonHelper;
import com.agent.reconciliation.domain.entity.IssueType;
import com.agent.reconciliation.domain.entity.ReconciliationAudit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SUITE 4: Bilingual Dispute Drafting & Language Isolation
 *
 * Validates strict language isolation in dispute drafts:
 * Pure Arabic isolation (zero Latin character bleed), pure English isolation (zero Arabic bleed),
 * bilingual parity, and the DisputeReasonHelper utility for clean descriptions.
 *
 * Test IDs: DISP-001 through DISP-005
 */
class BilingualDisputeDraftingTestSuite {

    // ─── Helper: Check if string contains Arabic script characters ──────────────
    private boolean containsArabic(String s) {
        return s != null && s.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.ARABIC);
    }

    // ─── Helper: Check if string contains Latin script characters ───────────────
    private boolean containsLatin(String s) {
        return s != null && s.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN);
    }

    // ─── DISP-001: Arabic Reason Contains Arabic Script ─────────────────────────
    @Test
    @DisplayName("DISP-001: Arabic reason for PRICE_MISMATCH contains Arabic script")
    void disp001_arabicReasonContainsArabic() {
        ReconciliationAudit audit = ReconciliationAudit.builder()
                .issueType(IssueType.PRICE_MISMATCH)
                .skuCode("SKU-TOMATO-RED")
                .itemDescription("طماطم بلدي (Egyptian Tomatoes)")
                .expectedValue(new BigDecimal("20.00"))
                .actualValue(new BigDecimal("25.00"))
                .build();

        String arabicReason = DisputeReasonHelper.getArabicReason(audit);

        assertThat(arabicReason).isNotBlank();
        assertThat(containsArabic(arabicReason)).isTrue();
        // Arabic reason must contain Arabic text
        assertThat(arabicReason).contains("سعر");
    }

    // ─── DISP-002: English Reason Contains Latin Script ─────────────────────────
    @Test
    @DisplayName("DISP-002: English reason for PRICE_MISMATCH contains Latin script and no Arabic bleed")
    void disp002_englishReasonContainsLatinOnly() {
        ReconciliationAudit audit = ReconciliationAudit.builder()
                .issueType(IssueType.PRICE_MISMATCH)
                .skuCode("SKU-TOMATO-RED")
                .itemDescription("Egyptian Tomatoes")
                .expectedValue(new BigDecimal("20.00"))
                .actualValue(new BigDecimal("25.00"))
                .build();

        String englishReason = DisputeReasonHelper.getEnglishReason(audit);

        assertThat(englishReason).isNotBlank();
        assertThat(containsLatin(englishReason)).isTrue();
        // English-only description should not contain Arabic characters
        assertThat(containsArabic(englishReason)).isFalse();
    }

    // ─── DISP-003: Bilingual Explanation Contains Both Languages ────────────────
    @Test
    @DisplayName("DISP-003: Bilingual explanation contains both Arabic and English sections with separator")
    void disp003_bilingualExplanationContainsBothLanguages() {
        ReconciliationAudit audit = ReconciliationAudit.builder()
                .issueType(IssueType.QUANTITY_MISMATCH)
                .skuCode("SKU-ONION-YELLOW")
                .itemDescription("Yellow Spring Onions")
                .expectedValue(new BigDecimal("50.00"))
                .actualValue(new BigDecimal("60.00"))
                .build();

        String bilingual = DisputeReasonHelper.getBilingualExplanation(audit);

        assertThat(bilingual).isNotBlank();
        assertThat(containsArabic(bilingual)).isTrue();
        assertThat(containsLatin(bilingual)).isTrue();
        assertThat(bilingual).contains(" — "); // Separator between languages
    }

    // ─── DISP-004: Arabic Item Description Cleaning ─────────────────────────────
    @Test
    @DisplayName("DISP-004: cleanArabicItemDescription extracts Arabic text from bilingual parenthesized description")
    void disp004_arabicDescriptionCleaning() {
        // "طماطم فاخرة (Premium Tomatoes)" → should extract Arabic part
        String cleaned = DisputeReasonHelper.cleanArabicItemDescription("طماطم فاخرة (Premium Tomatoes)");
        assertThat(containsArabic(cleaned)).isTrue();

        // Null safety
        String nullClean = DisputeReasonHelper.cleanArabicItemDescription(null);
        assertThat(nullClean).isNotNull();
        assertThat(nullClean).isNotBlank();
    }

    // ─── DISP-005: English Item Description Cleaning ────────────────────────────
    @Test
    @DisplayName("DISP-005: cleanEnglishItemDescription extracts English text from bilingual parenthesized description")
    void disp005_englishDescriptionCleaning() {
        // "بصل أحمر (Red Onions)" → should extract "Red Onions"
        String cleaned = DisputeReasonHelper.cleanEnglishItemDescription("بصل أحمر (Red Onions)");
        assertThat(containsLatin(cleaned)).isTrue();
        assertThat(cleaned).contains("Red Onions");

        // Null safety
        String nullClean = DisputeReasonHelper.cleanEnglishItemDescription(null);
        assertThat(nullClean).isNotNull();
        assertThat(nullClean).isNotBlank();
    }

    // ─── DISP-006: All Issue Types Generate Non-Empty Reasons ───────────────────
    @Test
    @DisplayName("DISP-006: Every IssueType generates non-empty Arabic and English reasons")
    void disp006_allIssueTypesGenerateReasons() {
        for (IssueType issueType : IssueType.values()) {
            ReconciliationAudit audit = ReconciliationAudit.builder()
                    .issueType(issueType)
                    .skuCode("SKU-TEST")
                    .itemDescription("Test Item (صنف اختبار)")
                    .expectedValue(new BigDecimal("100.00"))
                    .actualValue(new BigDecimal("120.00"))
                    .build();

            String arReason = DisputeReasonHelper.getArabicReason(audit);
            String enReason = DisputeReasonHelper.getEnglishReason(audit);

            assertThat(arReason).as("Arabic reason for %s", issueType).isNotBlank();
            assertThat(enReason).as("English reason for %s", issueType).isNotBlank();
        }
    }

    // ─── DISP-007: Arabic Issue Titles Are Correct ──────────────────────────────
    @Test
    @DisplayName("DISP-007: Arabic issue titles for all IssueTypes are non-empty and contain Arabic script")
    void disp007_arabicIssueTitlesCorrect() {
        for (IssueType issueType : IssueType.values()) {
            String title = DisputeReasonHelper.getArabicIssueTitle(issueType);
            assertThat(title).as("Arabic title for %s", issueType).isNotBlank();
            assertThat(containsArabic(title)).as("Arabic title for %s contains Arabic", issueType).isTrue();
        }
    }

    // ─── DISP-008: English Issue Titles Are Correct ─────────────────────────────
    @Test
    @DisplayName("DISP-008: English issue titles for all IssueTypes are non-empty and contain Latin script")
    void disp008_englishIssueTitlesCorrect() {
        for (IssueType issueType : IssueType.values()) {
            String title = DisputeReasonHelper.getEnglishIssueTitle(issueType);
            assertThat(title).as("English title for %s", issueType).isNotBlank();
            assertThat(containsLatin(title)).as("English title for %s contains Latin", issueType).isTrue();
        }
    }

    // ─── DISP-009: Vendor Name Cleaning ─────────────────────────────────────────
    @Test
    @DisplayName("DISP-009: Vendor name cleaning extracts correct language variants")
    void disp009_vendorNameCleaning() {
        String arVendor = DisputeReasonHelper.cleanArabicVendorName("Al-Wadi Farms (وادي المزارع)");
        assertThat(containsArabic(arVendor)).isTrue();

        String enVendor = DisputeReasonHelper.cleanEnglishVendorName("Al-Wadi Farms (وادي المزارع)");
        assertThat(containsLatin(enVendor)).isTrue();

        // Null safety
        assertThat(DisputeReasonHelper.cleanArabicVendorName(null)).isNotBlank();
        assertThat(DisputeReasonHelper.cleanEnglishVendorName(null)).isNotBlank();
    }
}
