package com.agent.reconciliation.controller;

import com.agent.reconciliation.domain.dto.FinancialRoiSummaryResponse;
import com.agent.reconciliation.service.FinancialAnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller serving portfolio financial analytics, cash leakage blocked metrics,
 * and CFO executive summaries.
 */
@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

    private final FinancialAnalyticsService financialAnalyticsService;

    public AnalyticsController(FinancialAnalyticsService financialAnalyticsService) {
        this.financialAnalyticsService = financialAnalyticsService;
    }

    /**
     * Returns portfolio-level ROI metrics, cash leakage intercepted, and AI executive brief.
     */
    @GetMapping("/roi-summary")
    public ResponseEntity<FinancialRoiSummaryResponse> getRoiSummary() {
        log.info("--> [GET /api/analytics/roi-summary] Fetching financial ROI and cash leakage summary");
        FinancialRoiSummaryResponse summary = financialAnalyticsService.getRoiSummary();
        return ResponseEntity.ok(summary);
    }
}
