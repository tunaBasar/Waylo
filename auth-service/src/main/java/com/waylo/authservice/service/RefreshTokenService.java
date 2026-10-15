package com.waylo.authservice.service;

import com.waylo.authservice.config.JwtUtil;
import com.waylo.authservice.domain.RefreshToken;
import com.waylo.authservice.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenService(JwtUtil jwtUtil, RefreshTokenRepository refreshTokenRepository) {
        this.jwtUtil = jwtUtil;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Issue a new refresh token for the given user.
     * If familyId is null, a new random family is created.
     */
    @Transactional
    public String issue(UUID userId, UUID familyId) {
        UUID actualFamily = (familyId != null) ? familyId : UUID.randomUUID();
        UUID jti = UUID.randomUUID();

        String token = jwtUtil.generateRefreshToken(userId, jti);
        Instant expiresAt = jwtUtil.getExpiration(token);

        RefreshToken entity = new RefreshToken(jti, userId, actualFamily, expiresAt);
        refreshTokenRepository.save(entity);

        return token;
    }

    /**
     * Rotate a single-use refresh token.
     * Validates the token, acquires a pessimistic lock on the DB row, checks
     * for reuse/revocation/expiry, marks the row as used, and issues a new
     * token in the same family.
     *
     * @throws InvalidRefreshTokenException on invalid, expired, revoked, or reused tokens
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotationResult rotate(String refreshToken) {
        // 1. Validate JWT signature and typ — fail fast without DB lookup
        if (!jwtUtil.isValidRefreshToken(refreshToken)) {
            throw new InvalidRefreshTokenException("Gecersiz refresh token");
        }

        UUID jti = UUID.fromString(jwtUtil.getJti(refreshToken));
        UUID userId = UUID.fromString(jwtUtil.getUserId(refreshToken));

        // 2. Load row with pessimistic write lock
        RefreshToken token = refreshTokenRepository.findForUpdate(jti);
        if (token == null) {
            throw new InvalidRefreshTokenException("Refresh token bulunamadi");
        }

        Instant now = Instant.now();

        // 3. Check revoked
        if (token.getRevokedAt() != null) {
            throw new InvalidRefreshTokenException("Refresh token iptal edilmis");
        }

        // 4. Check reuse — already used => theft => revoke family
        if (token.getUsedAt() != null) {
            log.warn("Refresh token reuse detected for user {} family {}", userId, token.getFamilyId());
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            throw new InvalidRefreshTokenException("Gecersiz refresh token");
        }

        // 5. Check expiry
        if (token.getExpiresAt().isBefore(now)) {
            throw new InvalidRefreshTokenException("Refresh token suresi dolmus");
        }

        // 6. Mark as used and issue new token in the same family
        token.setUsedAt(now);
        refreshTokenRepository.save(token);

        String newRefreshToken = issue(userId, token.getFamilyId());

        return new RotationResult(userId, newRefreshToken);
    }

    /**
     * Revoke all refresh tokens for a user.
     */
    @Transactional
    public void revokeAll(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    /**
     * Revoke the entire family identified by the given refresh token.
     * No-op if the token is invalid or has no valid rows in the family.
     */
    @Transactional
    public void revokeByToken(String refreshToken) {
        if (refreshToken == null || !jwtUtil.isValidRefreshToken(refreshToken)) {
            return; // no-op — caller should not leak validity
        }

        UUID jti = UUID.fromString(jwtUtil.getJti(refreshToken));

        RefreshToken token = refreshTokenRepository.findForUpdate(jti);
        if (token == null) {
            return; // no-op
        }

        refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now());
    }
}
