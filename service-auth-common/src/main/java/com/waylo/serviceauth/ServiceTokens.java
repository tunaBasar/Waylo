package com.waylo.serviceauth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compact JWT (alg EdDSA / Ed25519) for service-to-service calls.
 * Claims: iss = calling service, aud = target service, iat, exp (at most 60 s after iat), jti.
 * The claim set is flat (strings and numbers only), so a tiny parser is enough and no JSON library is needed.
 */
public final class ServiceTokens {

    public static final String HEADER = "X-Service-Token";
    public static final long MAX_TTL_SECONDS = 60;
    /** Tolerated clock skew between containers. */
    static final long SKEW_SECONDS = 5;

    private static final String JWT_HEADER = b64("{\"alg\":\"EdDSA\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    private static final Pattern CLAIM = Pattern.compile("\"([a-z]+)\"\\s*:\\s*(?:\"([A-Za-z0-9._-]*)\"|(-?\\d+))");
    private static final Pattern NAME = Pattern.compile("[a-z0-9-]{1,64}");

    private ServiceTokens() {
    }

    /** Thrown with a short, non-secret reason when a token is rejected. */
    public static class InvalidServiceToken extends Exception {
        public InvalidServiceToken(String reason) {
            super(reason);
        }
    }

    public static String sign(String issuer, String audience, PrivateKey key, Instant now, long ttlSeconds) {
        if (!NAME.matcher(issuer).matches() || !NAME.matcher(audience).matches()) {
            throw new IllegalArgumentException("service names must match " + NAME.pattern());
        }
        long iat = now.getEpochSecond();
        String claims = "{\"iss\":\"" + issuer + "\",\"aud\":\"" + audience + "\",\"iat\":" + iat
                + ",\"exp\":" + (iat + Math.min(ttlSeconds, MAX_TTL_SECONDS)) + ",\"jti\":\"" + UUID.randomUUID() + "\"}";
        String signingInput = JWT_HEADER + "." + b64(claims.getBytes(StandardCharsets.UTF_8));
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(key);
            s.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + b64(s.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 signing failed", e);
        }
    }

    /** Verified claims of an accepted token. */
    public record Claims(String issuer, String audience, long expiresAt, String jti) {
    }

    /**
     * Verifies signature (against the issuer's public key), audience, lifetime. Does not check the route allowlist
     * or replay; {@link ServiceAuthenticator} does that.
     */
    public static Claims verify(String token, String expectedAudience, Function<String, PublicKey> keyOfIssuer, Instant now)
            throws InvalidServiceToken {
        if (token == null || token.length() > 2048) throw new InvalidServiceToken("malformed");
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) throw new InvalidServiceToken("malformed");
        if (!JWT_HEADER.equals(parts[0])) throw new InvalidServiceToken("unsupported header");
        Map<String, String> claims;
        byte[] sig;
        try {
            claims = parseClaims(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            sig = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new InvalidServiceToken("malformed");
        }
        String iss = claims.get("iss");
        if (iss == null) throw new InvalidServiceToken("missing iss");
        PublicKey key = keyOfIssuer.apply(iss);
        if (key == null) throw new InvalidServiceToken("unknown issuer");
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!s.verify(sig)) throw new InvalidServiceToken("bad signature");
        } catch (GeneralSecurityException e) {
            throw new InvalidServiceToken("bad signature");
        }
        if (!expectedAudience.equals(claims.get("aud"))) throw new InvalidServiceToken("wrong audience");
        long iat = number(claims, "iat");
        long exp = number(claims, "exp");
        long t = now.getEpochSecond();
        if (exp - iat > MAX_TTL_SECONDS || exp < iat) throw new InvalidServiceToken("lifetime too long");
        if (iat > t + SKEW_SECONDS) throw new InvalidServiceToken("issued in the future");
        if (exp + SKEW_SECONDS < t) throw new InvalidServiceToken("expired");
        String jti = claims.get("jti");
        if (jti == null || jti.isEmpty()) throw new InvalidServiceToken("missing jti");
        return new Claims(iss, claims.get("aud"), exp, jti);
    }

    private static long number(Map<String, String> claims, String name) throws InvalidServiceToken {
        try {
            return Long.parseLong(claims.get(name));
        } catch (NumberFormatException e) {
            throw new InvalidServiceToken("missing " + name);
        }
    }

    static Map<String, String> parseClaims(String json) {
        Map<String, String> out = new HashMap<>();
        Matcher m = CLAIM.matcher(json);
        while (m.find()) {
            String key = m.group(1);
            String value = m.group(2) != null ? m.group(2) : m.group(3);
            if (out.putIfAbsent(key, value) != null) throw new IllegalArgumentException("duplicate claim");
        }
        return out;
    }

    /** Base64 (standard or url-safe) DER PKCS#8 Ed25519 private key, as written by scripts/gen-service-keys.py. */
    public static PrivateKey privateKey(String base64Der) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(decode(base64Der)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("invalid Ed25519 private key", e);
        }
    }

    /** Base64 DER X.509 SubjectPublicKeyInfo Ed25519 public key. */
    public static PublicKey publicKey(String base64Der) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(decode(base64Der)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("invalid Ed25519 public key", e);
        }
    }

    /** WAYLO_SERVICE_KEYS_&lt;SERVICE&gt; environment variable name for a service. */
    public static String publicKeyEnvName(String service) {
        return "WAYLO_SERVICE_KEYS_" + service.toUpperCase().replace('-', '_');
    }

    private static byte[] decode(String s) {
        String t = s.trim();
        return (t.indexOf('-') >= 0 || t.indexOf('_') >= 0 ? Base64.getUrlDecoder() : Base64.getDecoder()).decode(t);
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
