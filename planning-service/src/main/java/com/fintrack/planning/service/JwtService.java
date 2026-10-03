package com.fintrack.planning.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Stateless, validation-only JWT utility for the Planning Service.
 *
 */
@Service
public class JwtService {

    @Value("${fintrack.jwt.secret}")
    private String secret;

    /**
     * The local-development fallback from {@code application.yml}. It is public (it lives in the
     * repository), so any token signed with it can be forged by anyone.
     */
    private static final String LOCAL_DEV_SECRET = "fintrack-local-development-jwt-secret-2026";

    /** Injected by Render on every web service; empty when running locally. */
    @Value("${RENDER_EXTERNAL_HOSTNAME:}")
    private String renderHostname;

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.length() < 32 || secret.contains("change-me")) {
            throw new IllegalStateException(
                    "FINTRACK_JWT_SECRET must be set to an unpredictable value of at least 32 characters");
        }
        if (LOCAL_DEV_SECRET.equals(secret) && renderHostname != null && !renderHostname.isBlank()) {
            throw new IllegalStateException(
                    "FINTRACK_JWT_SECRET is not set: refusing to start on Render with the public "
                            + "local-development JWT secret");
        }
    }

    public String extractUserId(String token) {
        Claims claims = parseClaims(token);
        String userId = claims.get("userId", String.class);
        return userId != null ? userId : claims.getSubject();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
