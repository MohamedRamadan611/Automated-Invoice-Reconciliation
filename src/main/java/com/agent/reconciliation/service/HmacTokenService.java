package com.agent.reconciliation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Service generating and verifying time-limited HMAC-SHA256 signed tokens
 * for secure one-click manager payment override links.
 * Token format: invoiceId:expirationEpochSeconds:urlSafeBase64Signature
 */
@Service
public class HmacTokenService {

    private static final Logger log = LoggerFactory.getLogger(HmacTokenService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secretKey;
    private final long ttlSeconds;

    public HmacTokenService(
            @Value("${app.hmac.secret-key:default-override-secret-change-me-in-production}") String secretKey,
            @Value("${app.hmac.ttl-seconds:86400}") long ttlSeconds) {
        this.secretKey = secretKey;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Result of token verification.
     */
    public record TokenVerificationResult(boolean valid, boolean expired, boolean tampered, Long invoiceId, String message) {}

    /**
     * Generates a time-limited HMAC-SHA256 token for the given invoice ID.
     *
     * @param invoiceId the invoice to generate an override token for
     * @return URL-safe token string: "invoiceId:expirationEpoch:base64Signature"
     */
    public String generateToken(Long invoiceId) {
        long expiration = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = invoiceId + ":" + expiration;
        String signature = computeSignature(payload);
        return payload + ":" + signature;
    }

    /**
     * Verifies a token's authenticity, expiration, and integrity.
     *
     * @param token the token string to verify
     * @return verification result with detailed status
     */
    public TokenVerificationResult verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return new TokenVerificationResult(false, false, true, null, "Token is empty or null.");
        }

        String[] parts = token.split(":", 3);
        if (parts.length != 3) {
            return new TokenVerificationResult(false, false, true, null, "Malformed token: expected 3 colon-separated segments.");
        }

        Long invoiceId;
        long expiration;
        try {
            invoiceId = Long.parseLong(parts[0]);
            expiration = Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            return new TokenVerificationResult(false, false, true, null, "Malformed token: non-numeric invoice ID or expiration.");
        }

        // Check expiration
        if (Instant.now().getEpochSecond() > expiration) {
            log.warn("HMAC token expired for invoice ID: {}. Expired at epoch: {}", invoiceId, expiration);
            return new TokenVerificationResult(false, true, false, invoiceId, "Token has expired.");
        }

        // Verify signature
        String payload = invoiceId + ":" + expiration;
        String expectedSignature = computeSignature(payload);
        if (!constantTimeEquals(expectedSignature, parts[2])) {
            log.warn("HMAC signature mismatch for invoice ID: {}. Possible tampering detected.", invoiceId);
            return new TokenVerificationResult(false, false, true, invoiceId, "Invalid signature: token has been tampered with.");
        }

        return new TokenVerificationResult(true, false, false, invoiceId, "Token is valid.");
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
