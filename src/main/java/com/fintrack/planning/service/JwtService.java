package com.fintrack.planning.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Stateless, validation-only JWT utility for the Planning Service.
 *
 * <p>Unlike {@code auth-service}, this service never issues tokens — it only
 * verifies the signature/expiry of tokens issued by {@code auth-service} and
 * extracts the {@code userId} claim so requests can be scoped to their owner.
 *
 * <p>The signing secret is shared with {@code auth-service} via the
 * {@code FINTRACK_JWT_SECRET} environment variable convention already used
 * across the platform (see {@code fintrack.jwt.secret} in {@code application.yml}).
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Service
public class JwtService {

    @Value("${fintrack.jwt.secret}")
    private String secret;

    /**
     * Extracts the {@code userId} claim embedded by {@code auth-service} at login time.
     *
     * @param token the JWT string
     * @return the user's ID
     * @throws io.jsonwebtoken.JwtException if the token is malformed, expired, or has an invalid signature
     */
    public String extractUserId(String token) {
        Claims claims = parseClaims(token);
        String userId = claims.get("userId", String.class);
        return userId != null ? userId : claims.getSubject();
    }

    /**
     * Parses and verifies a JWT, returning its claims.
     *
     * @param token the JWT string
     * @return the verified claims payload
     * @throws io.jsonwebtoken.JwtException if the token is malformed, expired, or has an invalid signature
     */
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
