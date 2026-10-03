package com.fintrack.planning.service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService}.
 *
 */
class JwtServiceTest {

    /** Must be ≥ 256 bits (32 bytes) for HS256. */
    private static final String SECRET = "fintrack-test-secret-key-32-bytes-ok!!";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
    }

    @Test
    void extractUserId_returnsUserIdFromClaim() {
        String token = buildToken("user-42", "user-42", futureExpiry());
        assertThat(jwtService.extractUserId(token)).isEqualTo("user-42");
    }

    @Test
    void extractUserId_fallsBackToSubjectWhenNoUserIdClaim() {
        // Build a token that has only a subject, no explicit userId claim
        String token = Jwts.builder()
                .subject("subject-only-user")
                .expiration(futureExpiry())
                .signWith(signingKey())
                .compact();

        assertThat(jwtService.extractUserId(token)).isEqualTo("subject-only-user");
    }

    @Test
    void extractUserId_prefersUserIdClaimOverSubjectWhenBothPresent() {
        // userId claim and subject differ — userId claim should win
        String token = buildToken("claim-user", "subject-user", futureExpiry());
        assertThat(jwtService.extractUserId(token)).isEqualTo("claim-user");
    }

    @Test
    void extractUserId_throwsForExpiredToken() {
        Date pastExpiry = new Date(System.currentTimeMillis() - 5_000); // 5 seconds ago
        String token = buildToken("user-1", "user-1", pastExpiry);

        assertThatThrownBy(() -> jwtService.extractUserId(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractUserId_throwsForInvalidSignature() {
        // Sign with a different key
        String differentSecret = "different-secret-key-32-bytes-ok!";
        SecretKey otherKey = Keys.hmacShaKeyFor(differentSecret.getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .subject("user-1")
                .claim("userId", "user-1")
                .expiration(futureExpiry())
                .signWith(otherKey)
                .compact();

        assertThatThrownBy(() -> jwtService.extractUserId(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractUserId_throwsForMalformedToken() {
        assertThatThrownBy(() -> jwtService.extractUserId("not.a.valid.jwt.token"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractUserId_throwsForEmptyString() {
        assertThatThrownBy(() -> jwtService.extractUserId(""))
                .isInstanceOf(Exception.class);
    }

    // ── validateSecret: public local-development secret must never be used on Render ──

    /** Same value as the fallback in application.yml (public, because it is in the repository). */
    private static final String LOCAL_DEV_SECRET = "fintrack-local-development-jwt-secret-2026";

    private static final String RENDER_HOSTNAME = "planning-service-fintrack.onrender.com";

    @Test
    void validateSecret_allowsLocalDevSecretWhenNotOnRender() {
        ReflectionTestUtils.setField(jwtService, "secret", LOCAL_DEV_SECRET);
        ReflectionTestUtils.setField(jwtService, "renderHostname", "");

        assertThatCode(() -> jwtService.validateSecret()).doesNotThrowAnyException();
    }

    @Test
    void validateSecret_rejectsLocalDevSecretOnRender() {
        ReflectionTestUtils.setField(jwtService, "secret", LOCAL_DEV_SECRET);
        ReflectionTestUtils.setField(jwtService, "renderHostname", RENDER_HOSTNAME);

        assertThatThrownBy(() -> jwtService.validateSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FINTRACK_JWT_SECRET");
    }

    @Test
    void validateSecret_acceptsCustomSecretOnRender() {
        ReflectionTestUtils.setField(jwtService, "renderHostname", RENDER_HOSTNAME);

        assertThatCode(() -> jwtService.validateSecret()).doesNotThrowAnyException();
    }

    @Test
    void validateSecret_stillRejectsShortSecret() {
        ReflectionTestUtils.setField(jwtService, "secret", "too-short");

        assertThatThrownBy(() -> jwtService.validateSecret())
                .isInstanceOf(IllegalStateException.class);
    }

    private String buildToken(String userId, String subject, Date expiry) {
        return Jwts.builder()
                .claim("userId", userId)
                .subject(subject)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private Date futureExpiry() {
        return new Date(System.currentTimeMillis() + 3_600_000); // 1 hour
    }
}
