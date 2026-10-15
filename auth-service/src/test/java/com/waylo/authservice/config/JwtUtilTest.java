package com.waylo.authservice.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JwtUtilTest {

    private final SecureRandom secureRandom = new SecureRandom();
    private byte[] testKeyBytes = new byte[32];
    private final String testKeyBase64;
    private final SecretKey testKey;
    private final JwtUtil jwtUtil;

    JwtUtil createJwtUtil(long expiration) {
        return new JwtUtil(
                testKeyBase64,
                expiration,
                604800000L
        );
    }

    JwtUtilTest() {
        secureRandom.nextBytes(testKeyBytes);
        testKeyBase64 = Base64.getEncoder().encodeToString(testKeyBytes);
        testKey = Keys.hmacShaKeyFor(testKeyBytes);
        jwtUtil = new JwtUtil(
                testKeyBase64,
                86400000L,
                604800000L
        );
    }

    @Test
    void accessToken_hasTypAccessAndCorrectClaims() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String role = "USER";

        String token = jwtUtil.generateToken(userId, email, role);

        var claims = jwtUtil.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get(JwtUtil.CLAIM_TYPE, String.class)).isEqualTo(JwtUtil.TYPE_ACCESS);
        assertThat(claims.get("email", String.class)).isEqualTo(email);
        assertThat(claims.get("role", String.class)).isEqualTo(role);
        assertThat(claims.getIssuer()).isEqualTo(JwtUtil.ISSUER);
    }

    @Test
    void refreshToken_hasTypRefreshAndIssuer() {
        UUID userId = UUID.randomUUID();

        String token = jwtUtil.generateRefreshToken(userId);

        var claims = jwtUtil.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get(JwtUtil.CLAIM_TYPE, String.class)).isEqualTo(JwtUtil.TYPE_REFRESH);
        assertThat(claims.getIssuer()).isEqualTo(JwtUtil.ISSUER);
        assertThat(claims.getId()).isNotNull();
    }

    @Test
    void key_security_accessToken_isNotAValidRefreshToken() {
        UUID userId = UUID.randomUUID();
        String accessToken = jwtUtil.generateToken(userId, "test@example.com", "USER");
        assertThat(jwtUtil.isValidRefreshToken(accessToken)).isFalse();
    }

    @Test
    void refreshToken_isValidRefreshToken_returnsTrue() {
        UUID userId = UUID.randomUUID();
        String refreshToken = jwtUtil.generateRefreshToken(userId);
        assertThat(jwtUtil.isValidRefreshToken(refreshToken)).isTrue();
    }

    @Test
    void tokenSignedWithDifferentSecret_isInvalid() {
        SecureRandom otherRandom = new SecureRandom();
        byte[] otherBytes = new byte[32];
        otherRandom.nextBytes(otherBytes);
        String otherKeyBase64 = Base64.getEncoder().encodeToString(otherBytes);
        JwtUtil otherJwt = new JwtUtil(
                otherKeyBase64,
                86400000L,
                604800000L
        );

        String token = jwtUtil.generateToken(UUID.randomUUID(), "test@example.com", "USER");

        assertThat(otherJwt.isValid(token)).isFalse();
        assertThat(otherJwt.isValidRefreshToken(token)).isFalse();
    }

    @Test
    void expiredToken_isInvalid() {
        JwtUtil expiredJwt = createJwtUtil(-1000);
        String token = expiredJwt.generateToken(UUID.randomUUID(), "test@example.com", "USER");

        assertThat(expiredJwt.isValid(token)).isFalse();
    }

    @Test
    void blankSecret_throwsIllegalStateException() {
        assertThatThrownBy(() -> new JwtUtil(
                        "   ",
                        86400000,
                        604800000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET must be set");
    }

    @Test
    void nullSecret_throwsIllegalStateException() {
        assertThatThrownBy(() -> new JwtUtil(
                        null,
                        86400000,
                        604800000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET must be set");
    }

    @Test
    void shortSecret_16Bytes_throwsIllegalStateException() {
        byte[] shortBytes = new byte[16];
        secureRandom.nextBytes(shortBytes);
        String shortKeyBase64 = Base64.getEncoder().encodeToString(shortBytes);

        assertThatThrownBy(() -> new JwtUtil(
                        shortKeyBase64,
                        86400000,
                        604800000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET must be at least 256 bits");
    }

    @Test
    void tokenWithoutIssuer_isInvalid() {
        String token = Jwts.builder()
                .subject("x")
                .signWith(testKey)
                .compact();

        assertThat(jwtUtil.isValid(token)).isFalse();
    }

    @Test
    void nullRefreshToken_isInvalid() {
        assertThat(jwtUtil.isValidRefreshToken((String) null)).isFalse();
    }
}
