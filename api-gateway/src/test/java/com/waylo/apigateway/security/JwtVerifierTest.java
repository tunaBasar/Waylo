package com.waylo.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtVerifierTest {

    // Generate a 64-byte secret and Base64-encode it so verifier's decode produces 64 bytes
    private static final String VALID_SECRET = Base64.getEncoder().encodeToString(
            UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)
    ) + Base64.getEncoder().encodeToString(
            UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)
    ) + Base64.getEncoder().encodeToString(
            UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)
    );

    private static final String WRONG_SECRET = Base64.getEncoder().encodeToString(
            UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)
    ) + Base64.getEncoder().encodeToString(
            UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)
    ) + Base64.getEncoder().encodeToString(
            UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)
    ) + "XDIFFERENT";

    private static final SecretKey VALID_KEY = Keys.hmacShaKeyFor(
            Base64.getDecoder().decode(VALID_SECRET)
    );

    private static final SecretKey WRONG_KEY = Keys.hmacShaKeyFor(
            Base64.getDecoder().decode(WRONG_SECRET)
    );

    private String buildToken(SecretKey key, String subject, String email, String role, String typ) {
        return Jwts.builder()
                .subject(subject)
                .claim("email", email)
                .claim("role", role)
                .claim("typ", typ)
                .issuer("waylo-auth")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    @Test
    void validAccessToken_accepted() {
        String token = buildToken(VALID_KEY, "user-1", "user@example.com", "USER", "access");
        JwtVerifier verifier = new JwtVerifier(VALID_SECRET);
        Optional<Claims> result = verifier.verifyAccessToken(token);
        assertThat(result).isPresent();
        assertThat(result.get().getSubject()).isEqualTo("user-1");
    }

    @Test
    void refreshToken_rejected() {
        String token = buildToken(VALID_KEY, "user-1", "user@example.com", "USER", "refresh");
        JwtVerifier verifier = new JwtVerifier(VALID_SECRET);
        Optional<Claims> result = verifier.verifyAccessToken(token);
        assertThat(result).isEmpty();
    }

    @Test
    void wrongKey_rejected() {
        String token = buildToken(VALID_KEY, "user-1", "user@example.com", "USER", "access");
        JwtVerifier verifier = new JwtVerifier(WRONG_SECRET);
        Optional<Claims> result = verifier.verifyAccessToken(token);
        assertThat(result).isEmpty();
    }

    @Test
    void wrongIssuer_rejected() {
        String token = Jwts.builder()
                .subject("user-1")
                .claim("email", "user@example.com")
                .claim("role", "USER")
                .claim("typ", "access")
                .issuer("other-auth")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(VALID_KEY)
                .compact();
        JwtVerifier verifier = new JwtVerifier(VALID_SECRET);
        Optional<Claims> result = verifier.verifyAccessToken(token);
        assertThat(result).isEmpty();
    }

    @Test
    void shortSecret_throwsIllegalStateException() {
        // "short" is invalid Base64 -> IllegalArgumentException from decoder
        // Verify that a valid Base64 string that decodes to < 32 bytes also throws
        String shortBase64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        assertThatThrownBy(() -> new JwtVerifier(shortBase64))
                .isInstanceOf(IllegalStateException.class);
    }
}
