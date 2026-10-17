package com.agent.reconciliation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Service generating and verifying time-limited HMAC-SHA256 signed tokens
 * for secure one-click manager payment override links and supplier self-service portal links.
 * Token formats:
 * - Override link: invoiceId:expirationEpochSeconds:urlSafeBase64Signature (24 hours)
 * - Vendor portal: invoiceId:PORTAL:expirationEpochSeconds:urlSafeBase64Signature (7 days)
 */
@Service
public class HmacTokenService {

    private static final Logger log = LoggerFactory.getLogger(HmacTokenService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public static final String PURPOSE_MANAGER_OVERRIDE = "OVERRIDE";
    public static final String PURPOSE_VENDOR_PORTAL = "PORTAL";

    private final String secretKey;
    private final long ttlSeconds;
    private final long portalTtlSeconds;

    public HmacTokenService(
            @Value("${app.hmac.secret-key:default-override-secret-change-me-in-production}") String secretKey,
            @Value("${app.hmac.ttl-seconds:86400}") long ttlSeconds) {
        this(secretKey, ttlSeconds, 604800L); // 7 days default for vendor dispute portal
    }

    @Autowired
    public HmacTokenService(
            @Value("${app.hmac.secret-key:default-override-secret-change-me-in-production}") String secretKey,
            @Value("${app.hmac.ttl-seconds:86400}") long ttlSeconds,
            @Value("${app.hmac.portal-ttl-seconds:604800}") long portalTtlSeconds) {
        this.secretKey = secretKey;
        this.ttlSeconds = ttlSeconds;
        this.portalTtlSeconds = portalTtlSeconds;
    }

    /**
     * Result of token verification.
     */
    public record TokenVerificationResult(
            boolean valid,
            boolean expired,
            boolean tampered,
            Long invoiceId,
            String purpose,
            String message
    ) {
        public TokenVerificationResult(boolean valid, boolean expired, boolean tampered, Long invoiceId, String message) {
            this(valid, expired, tampered, invoiceId, PURPOSE_MANAGER_OVERRIDE, message);
        }
    }

    /**
     * Generates a time-limited HMAC-SHA256 token for manager payment override (24 hours).
     */
    public String generateToken(Long invoiceId) {
        long expiration = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = invoiceId + ":" + expiration;
        String signature = computeSignature(payload);
        return payload + ":" + signature;
    }

    /**
     * Generates a time-limited HMAC-SHA256 token for supplier self-service portal (7 days).
     */
    public String generatePortalToken(Long invoiceId) {
        long expiration = Instant.now().getEpochSecond() + portalTtlSeconds;
        String payload = invoiceId + ":" + PURPOSE_VENDOR_PORTAL + ":" + expiration;
        String signature = computeSignature(payload);
        return payload + ":" + signature;
    }

    /**
     * Verifies a supplier portal token specifically.
     */
    public TokenVerificationResult verifyPortalToken(String token) {
        TokenVerificationResult result = verifyToken(token);
        if (!result.valid()) {
            return result;
        }
        if (!PURPOSE_VENDOR_PORTAL.equalsIgnoreCase(result.purpose())) {
            log.warn("Invalid token purpose. Expected '{}', got '{}'", PURPOSE_VENDOR_PORTAL, result.purpose());
            return new TokenVerificationResult(false, false, true, result.invoiceId(), result.purpose(),
                    "Invalid token purpose: not a vendor portal token.");
        }
        return result;
    }

    /**
     * Verifies a token's authenticity, expiration, and integrity.
     * Supports both 3-segment (legacy/override) and 4-segment (purpose-bound) tokens.
     */
    public TokenVerificationResult verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return new TokenVerificationResult(false, false, true, null, null, "Token is empty or null.");
        }

        String[] parts = token.split(":");
        if (parts.length != 3 && parts.length != 4) {
            return new TokenVerificationResult(false, false, true, null, null, "Malformed token: invalid segment count.");
        }

        Long invoiceId;
        String purpose;
        long expiration;
        String signature;
        String payload;

        try {
            if (parts.length == 3) {
                // 3 segments: invoiceId:expiration:signature
                invoiceId = Long.parseLong(parts[0]);
                purpose = PURPOSE_MANAGER_OVERRIDE;
                expiration = Long.parseLong(parts[1]);
                signature = parts[2];
                payload = invoiceId + ":" + expiration;
            } else {
                // 4 segments: invoiceId:purpose:expiration:signature
                invoiceId = Long.parseLong(parts[0]);
                purpose = parts[1];
                expiration = Long.parseLong(parts[2]);
                signature = parts[3];
                payload = invoiceId + ":" + purpose + ":" + expiration;
            }
        } catch (NumberFormatException ex) {
            return new TokenVerificationResult(false, false, true, null, null, "Malformed token: non-numeric invoice ID or expiration.");
        }

        // Check expiration
        if (Instant.now().getEpochSecond() > expiration) {
            log.warn("HMAC token expired for invoice ID: {}. Expired at epoch: {}", invoiceId, expiration);
            return new TokenVerificationResult(false, true, false, invoiceId, purpose, "Token has expired.");
        }

        // Verify signature
        String expectedSignature = computeSignature(payload);
        if (!constantTimeEquals(expectedSignature, signature)) {
            log.warn("HMAC signature mismatch for invoice ID: {}. Possible tampering detected.", invoiceId);
            return new TokenVerificationResult(false, false, true, invoiceId, purpose, "Invalid signature: token has been tampered with.");
        }

        return new TokenVerificationResult(true, false, false, invoiceId, purpose, "Token is valid.");
    }

    private String computeSignature(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to compute HMAC-SHA256 signature.", ex);
        }
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
