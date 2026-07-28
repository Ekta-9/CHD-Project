package com.ecgcare.backend.config;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256-signing";

    private JwtService jwtService;
    private JwtProperties properties;
    private final UUID doctorId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer("ecgcare");
        properties.setAccessTtlMinutes(15);
        properties.setRefreshTtlDays(7);
        jwtService = new JwtService(properties);
    }

    @Test
    void accessTokenContainsDoctorIdEmailAndSessionId() {
        String token = jwtService.generateAccessToken(doctorId, "doc@example.com", sessionId);

        assertThat(jwtService.extractDoctorId(token)).isEqualTo(doctorId);
        assertThat(jwtService.extractEmail(token)).isEqualTo("doc@example.com");
        assertThat(jwtService.extractSessionId(token)).isEqualTo(sessionId);
    }

    @Test
    void accessTokenHasIssuerAndFutureExpiration() {
        String token = jwtService.generateAccessToken(doctorId, "doc@example.com", sessionId);
        Claims claims = jwtService.extractAllClaims(token);

        assertThat(claims.getIssuer()).isEqualTo("ecgcare");
        assertThat(jwtService.extractExpiration(token)).isAfter(new Date());
        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    @Test
    void refreshTokenHasRefreshTypeAndNoEmail() {
        String token = jwtService.generateRefreshToken(doctorId, sessionId);
        Claims claims = jwtService.extractAllClaims(token);

        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
        assertThat(jwtService.extractEmail(token)).isNull();
        assertThat(jwtService.extractDoctorId(token)).isEqualTo(doctorId);
        assertThat(jwtService.extractSessionId(token)).isEqualTo(sessionId);
    }

    @Test
    void validateTokenReturnsTrueForFreshToken() {
        String token = jwtService.generateAccessToken(doctorId, "doc@example.com", sessionId);
        assertThat(jwtService.validateToken(token)).isTrue();
    }

    @Test
    void validateTokenReturnsFalseForExpiredToken() {
        properties.setAccessTtlMinutes(-5);
        String expired = jwtService.generateAccessToken(doctorId, "doc@example.com", sessionId);

        assertThat(jwtService.validateToken(expired)).isFalse();
    }

    @Test
    void extractClaimsThrowsForExpiredToken() {
        properties.setAccessTtlMinutes(-5);
        String expired = jwtService.generateAccessToken(doctorId, "doc@example.com", sessionId);

        assertThatThrownBy(() -> jwtService.extractAllClaims(expired))
                .isInstanceOf(Exception.class);
    }

    @Test
    void validateTokenReturnsFalseForTamperedToken() {
        String token = jwtService.generateAccessToken(doctorId, "doc@example.com", sessionId);
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThat(jwtService.validateToken(tampered)).isFalse();
    }

    @Test
    void validateTokenReturnsFalseForGarbageInput() {
        assertThat(jwtService.validateToken("not-a-jwt")).isFalse();
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtProperties otherProps = new JwtProperties();
        otherProps.setSecret("another-secret-key-that-is-also-long-enough-for-hmac-sha256");
        JwtService otherService = new JwtService(otherProps);

        String foreignToken = otherService.generateAccessToken(doctorId, "doc@example.com", sessionId);

        assertThat(jwtService.validateToken(foreignToken)).isFalse();
    }

    @Test
    void extractSessionIdReturnsNullWhenClaimMissing() {
        // Build a token without sessionId by using the raw builder path:
        // access/refresh tokens always carry one, so craft via a service with
        // claims-free token from another generate call is not possible - instead
        // assert the null branch through a token missing the claim.
        String token = io.jsonwebtoken.Jwts.builder()
                .setSubject(doctorId.toString())
                .setIssuer("ecgcare")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtService.extractSessionId(token)).isNull();
    }
}
