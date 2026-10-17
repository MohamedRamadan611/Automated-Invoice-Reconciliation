package com.agent.reconciliation.controller;

import com.agent.reconciliation.domain.dto.VendorScorecardResponse;
import com.agent.reconciliation.service.VendorScorecardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VendorController.class)
class VendorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VendorScorecardService vendorScorecardService;

    @Test
    @DisplayName("GET /api/vendors/scorecard: Returns list of vendor scorecards")
    void testGetAllVendorScorecards() throws Exception {
        VendorScorecardResponse scorecard = new VendorScorecardResponse(
                "Suez Cement",
                5,
                4,
                1,
                80.0,
                20.0,
                new BigDecimal("45000.00"),
                new BigDecimal("750.00"),
                "AUDIT_SCRUTINY",
                Collections.emptyList(),
                "Occasional price variances detected.",
                Instant.now()
        );

        when(vendorScorecardService.getVendorScorecards()).thenReturn(List.of(scorecard));

        mockMvc.perform(get("/api/vendors/scorecard")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].vendorName").value("Suez Cement"))
                .andExpect(jsonPath("$[0].tier").value("AUDIT_SCRUTINY"))
                .andExpect(jsonPath("$[0].cleanMatchRate").value(80.0));
    }

    @Test
    @DisplayName("GET /api/vendors/{vendorName}/scorecard: Returns 404 when vendor not found")
    void testGetVendorScorecard_NotFound() throws Exception {
        when(vendorScorecardService.getVendorScorecard("NonExistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/vendors/NonExistent/scorecard")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
