package com.agent.reconciliation.service;

import com.agent.reconciliation.domain.dto.VendorScorecardResponse;
import com.agent.reconciliation.domain.entity.Invoice;
import com.agent.reconciliation.domain.entity.IssueType;
import com.agent.reconciliation.domain.entity.ReconciliationAudit;
import com.agent.reconciliation.domain.entity.ReconciliationStatus;
import com.agent.reconciliation.repository.InvoiceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorScorecardServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    private VendorScorecardService service;

    @BeforeEach
    void setUp() {
        service = new VendorScorecardService(invoiceRepository, new ObjectMapper(), null, "gemini-3.8-flash", null);
    }

    @Test
    @DisplayName("getVendorScorecards: Correctly classifies Certified Partner (<3% discrepancy)")
    void testCertifiedPartnerClassification() {
        List<Invoice> invoices = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            invoices.add(Invoice.builder()
                    .id((long) i)
                    .invoiceNumber("INV-A-" + i)
                    .vendorName("Delta Logistics")
                    .invoicedTotal(new BigDecimal("1000.00"))
                    .reconciliationStatus(ReconciliationStatus.APPROVED)
                    .createdAt(Instant.now())
                    .build());
        }

        when(invoiceRepository.findAllWithAudits()).thenReturn(invoices);

        List<VendorScorecardResponse> scorecards = service.getVendorScorecards();

        assertThat(scorecards).hasSize(1);
        VendorScorecardResponse scorecard = scorecards.get(0);
        assertThat(scorecard.vendorName()).isEqualTo("Delta Logistics");
        assertThat(scorecard.tier()).isEqualTo(VendorScorecardService.TIER_CERTIFIED_PARTNER);
        assertThat(scorecard.cleanMatchRate()).isEqualTo(100.0);
        assertThat(scorecard.discrepancyRate()).isEqualTo(0.0);
        assertThat(scorecard.totalOverchargeAttempted()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getVendorScorecards: Correctly classifies Audit Scrutiny (3% - 15% discrepancy)")
    void testAuditScrutinyClassification() {
        List<Invoice> invoices = new ArrayList<>();
        // 9 approved, 1 discrepancy = 10% discrepancy
        for (int i = 1; i <= 9; i++) {
            invoices.add(Invoice.builder()
                    .id((long) i)
                    .invoiceNumber("INV-B-" + i)
                    .vendorName("Alexandria Steel")
                    .invoicedTotal(new BigDecimal("2000.00"))
                    .reconciliationStatus(ReconciliationStatus.APPROVED)
                    .createdAt(Instant.now())
                    .build());
        }

        Invoice discrepant = Invoice.builder()
                .id(10L)
                .invoiceNumber("INV-B-10")
                .vendorName("Alexandria Steel")
                .invoicedTotal(new BigDecimal("2500.00"))
                .reconciliationStatus(ReconciliationStatus.FLAGGED_DISCREPANCY)
                .createdAt(Instant.now())
                .build();
        discrepant.addAudit(ReconciliationAudit.builder()
                .id(201L)
                .invoice(discrepant)
                .issueType(IssueType.PRICE_MISMATCH)
                .skuCode("STEEL-REBAR-12")
                .itemDescription("12mm Rebar")
                .expectedValue(new BigDecimal("100.00"))
                .actualValue(new BigDecimal("150.00"))
                .explanation("Price overcharge")
                .build());
        invoices.add(discrepant);

        when(invoiceRepository.findAllWithAudits()).thenReturn(invoices);

        Optional<VendorScorecardResponse> scorecardOpt = service.getVendorScorecard("Alexandria Steel");

        assertThat(scorecardOpt).isPresent();
        VendorScorecardResponse scorecard = scorecardOpt.get();
        assertThat(scorecard.tier()).isEqualTo(VendorScorecardService.TIER_AUDIT_SCRUTINY);
        assertThat(scorecard.cleanMatchRate()).isEqualTo(90.0);
        assertThat(scorecard.discrepancyRate()).isEqualTo(10.0);
        assertThat(scorecard.totalOverchargeAttempted()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(scorecard.riskNarrative()).contains("occasional variances");
    }

    @Test
    @DisplayName("getVendorScorecards: Correctly classifies High-Risk Supplier (>15% discrepancy)")
    void testHighRiskClassification() {
        Invoice discrepant = Invoice.builder()
                .id(1L)
                .invoiceNumber("INV-C-1")
                .vendorName("Rogue Vendor Inc")
                .invoicedTotal(new BigDecimal("5000.00"))
                .reconciliationStatus(ReconciliationStatus.FLAGGED_DISCREPANCY)
                .createdAt(Instant.now())
                .build();
        discrepant.addAudit(ReconciliationAudit.builder()
                .id(301L)
                .invoice(discrepant)
                .issueType(IssueType.EXTRA_FEE)
                .itemDescription("Bogus surcharge")
                .expectedValue(BigDecimal.ZERO)
                .actualValue(new BigDecimal("800.00"))
                .explanation("Unauthorized fee")
                .build());

        when(invoiceRepository.findAllWithAudits()).thenReturn(List.of(discrepant));

        List<VendorScorecardResponse> scorecards = service.getVendorScorecards();

        assertThat(scorecards).hasSize(1);
        VendorScorecardResponse scorecard = scorecards.get(0);
        assertThat(scorecard.tier()).isEqualTo(VendorScorecardService.TIER_HIGH_RISK);
        assertThat(scorecard.cleanMatchRate()).isEqualTo(0.0);
        assertThat(scorecard.discrepancyRate()).isEqualTo(100.0);
        assertThat(scorecard.totalOverchargeAttempted()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(scorecard.riskNarrative()).contains("severe commercial risk");
    }
}
