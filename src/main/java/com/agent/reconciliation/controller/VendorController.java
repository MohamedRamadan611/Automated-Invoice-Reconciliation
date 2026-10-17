package com.agent.reconciliation.controller;

import com.agent.reconciliation.domain.dto.VendorScorecardResponse;
import com.agent.reconciliation.service.VendorScorecardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * REST controller serving vendor compliance scorecards, risk tiers, and supplier reliability trends.
 */
@RestController
@RequestMapping("/api/vendors")
@CrossOrigin(origins = "*")
public class VendorController {

    private static final Logger log = LoggerFactory.getLogger(VendorController.class);

    private final VendorScorecardService vendorScorecardService;

    public VendorController(VendorScorecardService vendorScorecardService) {
        this.vendorScorecardService = vendorScorecardService;
    }

    /**
     * Lists reliability scorecards for all suppliers in the system.
     */
    @GetMapping("/scorecard")
    public ResponseEntity<List<VendorScorecardResponse>> getAllVendorScorecards() {
        log.info("--> [GET /api/vendors/scorecard] Fetching all vendor reliability scorecards");
        List<VendorScorecardResponse> scorecards = vendorScorecardService.getVendorScorecards();
        return ResponseEntity.ok(scorecards);
    }

    /**
     * Fetches the reliability scorecard for a specific supplier by name.
     */
    @GetMapping("/{vendorName}/scorecard")
    public ResponseEntity<VendorScorecardResponse> getVendorScorecard(@PathVariable("vendorName") String vendorName) {
        log.info("--> [GET /api/vendors/{}/scorecard] Fetching vendor scorecard", vendorName);
        return vendorScorecardService.getVendorScorecard(vendorName)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found: " + vendorName));
    }
}
