package com.agent.reconciliation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacTokenServiceTest {

    private HmacTokenService hmacTokenService;

    @BeforeEach
    void setUp() {
        hmacTokenService = new HmacTokenService("test-secret-key-for-unit-testing-32bytes!", 3600L, 604800L);
    }

    @Test
    @DisplayName("generatePortalToken: Generates valid token with PORTAL purpose and 7 days TTL")
    void testGenerateAndVerifyPortalToken_Success() {
        Long invoiceId = 42L;
        String token = hmacTokenService.generatePortalToken(invoiceId);

        assertThat(token).isNotNull().contains(":PORTAL:");

        HmacTokenService.TokenVerificationResult result = hmacTokenService.verifyPortalToken(token);

        assertThat(result.valid()).isTrue();
        assertThat(result.expired()).isFalse();
        assertThat(result.tampered()).isFalse();
        assertThat(result.invoiceId()).isEqualTo(42L);
        assertThat(result.purpose()).isEqualTo(HmacTokenService.PURPOSE_VENDOR_PORTAL);
    }

    @Test
    @DisplayName("verifyPortalToken: Rejects tampered signature with tampered=true")
    void testVerifyPortalToken_TamperedSignature() {
        Long invoiceId = 42L;
        String token = hmacTokenService.generatePortalToken(invoiceId);
        String tamperedToken = token + "corrupted";

        HmacTokenService.TokenVerificationResult result = hmacTokenService.verifyPortalToken(tamperedToken);

        assertThat(result.valid()).isFalse();
        assertThat(result.tampered()).isTrue();
    }

    @Test
    @DisplayName("verifyPortalToken: Rejects expired token")
    void testVerifyPortalToken_ExpiredToken() {
        // Service with negative TTL so token is immediately expired
        HmacTokenService expiredService = new HmacTokenService("test-secret-key-for-unit-testing-32bytes!", -10L, -10L);
        String expiredToken = expiredService.generatePortalToken(99L);

        HmacTokenService.TokenVerificationResult result = hmacTokenService.verifyPortalToken(expiredToken);

        assertThat(result.valid()).isFalse();
        assertThat(result.expired()).isTrue();
    }

    @Test
    @DisplayName("verifyPortalToken: Rejects payment override token when presented to portal")
    void testVerifyPortalToken_WrongPurpose_Rejected() {
        Long invoiceId = 15L;
        // Generate an OVERRIDE token (24h)
        String overrideToken = hmacTokenService.generateToken(invoiceId);

        // Verify with portal verifier
        HmacTokenService.TokenVerificationResult result = hmacTokenService.verifyPortalToken(overrideToken);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("Invalid token purpose");
    }

    @Test
    @DisplayName("generateToken: Generates valid payment override token")
    void testGenerateAndVerifyOverrideToken_Success() {
        Long invoiceId = 100L;
        String token = hmacTokenService.generateToken(invoiceId);

        HmacTokenService.TokenVerificationResult result = hmacTokenService.verifyToken(token);

        assertThat(result.valid()).isTrue();
        assertThat(result.invoiceId()).isEqualTo(100L);
        assertThat(result.purpose()).isEqualTo(HmacTokenService.PURPOSE_MANAGER_OVERRIDE);
    }
}
