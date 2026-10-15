package com.waylo.authservice.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.time.Instant;

@Component
public class JwtUtil {

    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final String ISSUER = "waylo-auth";

    private final SecretKey key;
    private final long expiration;
    private final long refreshExpiration;

    public JwtUtil(
            @Value("${waylo.jwt.secret}") String secret,
            @Value("${waylo.jwt.expiration}") long expiration,
            @Value("${waylo.jwt.refresh-expiration}") long refreshExpiration) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be set");
        }
        byte[] decoded = Base64.getDecoder().decode(secret);
        if (decoded.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 256 bits");
        }
        this.key = Keys.hmacShaKeyFor(decoded);
        this.expiration = expiration;
        this.refreshExpiration = refreshExpiration;
    }

    public String generateToken(UUID userId, String email, String role) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuer(ISSUER)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        return generateRefreshTokenWithJti(userId, UUID.randomUUID());
    }

    public String generateRefreshToken(UUID userId, UUID jti) {
        return generateRefreshTokenWithJti(userId, jti);
    }

    private String generateRefreshTokenWithJti(UUID userId, UUID jti) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuer(ISSUER)
                .id(jti.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isValidRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getUserId(String token) {
        return parseToken(token).getSubject();
    }

    public String getEmail(String token) {
        return parseToken(token).get("email", String.class);
    }

    public String getJti(String token) {
        return parseToken(token).getId();
    }

    public Instant getExpiration(String token) {
        return parseToken(token).getExpiration().toInstant();
    }
}
