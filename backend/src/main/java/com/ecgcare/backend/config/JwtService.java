package com.ecgcare.backend.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID doctorId, String email, UUID sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sessionId", sessionId.toString());
        return createToken(claims, doctorId.toString(), email, jwtProperties.getAccessTtlMinutes(), ChronoUnit.MINUTES);
    }

    public String generateRefreshToken(UUID doctorId, UUID sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sessionId", sessionId.toString());
        claims.put("type", "refresh");
        return createToken(claims, doctorId.toString(), null, jwtProperties.getRefreshTtlDays(), ChronoUnit.DAYS);
    }

    private String createToken(Map<String, Object> claims, String subject, String email, long amount, ChronoUnit unit) {
        Instant now = Instant.now();
        Instant expiration = now.plus(amount, unit);

        var builder = Jwts.builder()
                .setSubject(subject)
                .setIssuer(jwtProperties.getIssuer())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration));

        claims.forEach(builder::claim);

        if (email != null) {
            builder.claim("email", email);
        }

        return builder.signWith(getSigningKey()).compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public UUID extractDoctorId(String token) {
        return UUID.fromString(extractClaim(token, Claims::getSubject));
    }

    public UUID extractSessionId(String token) {
        String sessionIdStr = extractClaim(token, claims -> claims.get("sessionId", String.class));
        return sessionIdStr != null ? UUID.fromString(sessionIdStr) : null;
    }

    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
}
