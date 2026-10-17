package com.agent.reconciliation.controller;

import com.agent.reconciliation.domain.dto.FinancialRoiSummaryResponse;
import com.agent.reconciliation.service.FinancialAnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FinancialAnalyticsService financialAnalyticsService;

    @Test
    @DisplayName("GET /api/analytics/roi-summary: Returns HTTP 200 with JSON payload")
    void testGetRoiSummary() throws Exception {
        FinancialRoiSummaryResponse mockResponse = new FinancialRoiSummaryResponse(
                new BigDecimal("50000.00"),
                new BigDecimal("1250.00"),
                new BigDecimal("800.00"),
                new BigDecimal("450.00"),
                BigDecimal.ZERO,
                5,
                4,
                1,
                0,
                80.0,
                Collections.emptyList(),
                "AI Executive brief content",
                "EGP"
        );

        when(financialAnalyticsService.getRoiSummary()).thenReturn(mockResponse);

        mockMvc.perform(get("/api/analytics/roi-summary")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalInvoicedVolume").value(50000.0))
                .andExpect(jsonPath("$.totalCashLeakageBlocked").value(1250.0))
                .andExpect(jsonPath("$.cleanInvoicesCount").value(4))
                .andExpect(jsonPath("$.accuracyRate").value(80.0))
                .andExpect(jsonPath("$.currency").value("EGP"));
    }
}
