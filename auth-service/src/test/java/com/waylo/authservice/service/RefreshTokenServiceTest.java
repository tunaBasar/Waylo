package com.waylo.authservice.service;

import com.waylo.authservice.config.JwtUtil;
import com.waylo.authservice.domain.RefreshToken;
import com.waylo.authservice.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import java.lang.annotation.Annotation;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private static final UUID USER_ID = UUID.fromString("a1b2c3d4-1111-2222-3333-444444444444");
    private static final UUID FAMILY_ID = UUID.fromString("b2c3d4e5-2222-3333-4444-555555555555");
    private static final UUID JTI = UUID.fromString("c3d4e5f6-3333-4444-5555-666666666666");
    private static final String REFRESH_TOKEN = "mock-refresh-token";
    private static final String NEW_REFRESH_TOKEN = "mock-new-refresh-token";
    private static final Instant FUTURE = Instant.now().plusSeconds(604800);
    private static final Instant PAST = Instant.now().minusSeconds(604800);

    @BeforeEach
    void setupJwtMocks() {
        lenient().when(jwtUtil.getJti(REFRESH_TOKEN)).thenReturn(JTI.toString());
        lenient().when(jwtUtil.getUserId(REFRESH_TOKEN)).thenReturn(USER_ID.toString());
    }

    // ---- Issue tests ----

    @Nested
    @DisplayName("Issue")
    class IssueTests {

        @Test
        @DisplayName("issue persists a new row and returns the token")
        void issue_persistsRow_andReturnsToken() {
            when(jwtUtil.generateRefreshToken(eq(USER_ID), any(UUID.class))).thenReturn(REFRESH_TOKEN);
            when(jwtUtil.getExpiration(REFRESH_TOKEN)).thenReturn(FUTURE);

            String token = refreshTokenService.issue(USER_ID, null);

            assertEquals(REFRESH_TOKEN, token);
            verify(refreshTokenRepository).save(argThat(entity -> {
                assertNotNull(entity.getJti());
                assertEquals(USER_ID, entity.getUserId());
                assertNotNull(entity.getFamilyId());
                assertEquals(FUTURE, entity.getExpiresAt());
                return true;
            }));
        }

        @Test
        @DisplayName("issue with given familyId uses that family")
        void issue_withFamilyId_usesGivenFamily() {
            when(jwtUtil.generateRefreshToken(eq(USER_ID), any(UUID.class))).thenReturn(REFRESH_TOKEN);
            when(jwtUtil.getExpiration(REFRESH_TOKEN)).thenReturn(FUTURE);

            String token = refreshTokenService.issue(USER_ID, FAMILY_ID);

            verify(refreshTokenRepository).save(argThat(entity -> {
                assertEquals(FAMILY_ID, entity.getFamilyId());
                return true;
            }));
        }
    }

    // ---- Rotate valid tests ----

    @Nested
    @DisplayName("Rotate valid token")
    class RotateValidTests {

        @Test
        @DisplayName("rotate sets usedAt and issues new token in same family")
        void rotate_valid_setsUsedAt_andIssuesNewToken() {
            RefreshToken validToken = new RefreshToken(JTI, USER_ID, FAMILY_ID, FUTURE);

            when(jwtUtil.isValidRefreshToken(REFRESH_TOKEN)).thenReturn(true);
            when(refreshTokenRepository.findForUpdate(JTI)).thenReturn(validToken);
            when(jwtUtil.generateRefreshToken(eq(USER_ID), any(UUID.class))).thenReturn(NEW_REFRESH_TOKEN);
            when(jwtUtil.getExpiration(NEW_REFRESH_TOKEN)).thenReturn(FUTURE);

            RotationResult result = refreshTokenService.rotate(REFRESH_TOKEN);

            assertEquals(USER_ID, result.userId());
            assertEquals(NEW_REFRESH_TOKEN, result.newRefreshToken());

            // usedAt should be set
            assertNotNull(validToken.getUsedAt());

            // two saves: usedAt-set on original + new token in same family
            verify(refreshTokenRepository, times(2)).save(any());
        }
    }

    // ---- Rotate reuse tests (theft detection) ----

    @Nested
    @DisplayName("Rotate reuse detection")
    class RotateReuseTests {

        @Test
        @DisplayName("rotate already-used token revokes family and throws")
        void rotate_alreadyUsed_revokesFamily_andThrows() {
            RefreshToken usedToken = new RefreshToken(JTI, USER_ID, FAMILY_ID, FUTURE);
            usedToken.setUsedAt(Instant.now());

            when(jwtUtil.isValidRefreshToken(REFRESH_TOKEN)).thenReturn(true);
            when(refreshTokenRepository.findForUpdate(JTI)).thenReturn(usedToken);

            assertThrows(InvalidRefreshTokenException.class,
                    () -> refreshTokenService.rotate(REFRESH_TOKEN));

            verify(refreshTokenRepository).revokeFamily(eq(FAMILY_ID), any(Instant.class));
        }
    }

    // ---- Rotate revoked tests ----

    @Nested
    @DisplayName("Rotate revoked token")
    class RotateRevokedTests {

        @Test
        @DisplayName("rotate revoked token throws exception")
        void rotate_revoked_throws() {
            RefreshToken revokedToken = new RefreshToken(JTI, USER_ID, FAMILY_ID, FUTURE);
            revokedToken.setRevokedAt(Instant.now());

            when(jwtUtil.isValidRefreshToken(REFRESH_TOKEN)).thenReturn(true);
            when(refreshTokenRepository.findForUpdate(JTI)).thenReturn(revokedToken);

            assertThrows(InvalidRefreshTokenException.class,
                    () -> refreshTokenService.rotate(REFRESH_TOKEN));
        }
    }

    // ---- Rotate missing row tests ----

    @Nested
    @DisplayName("Rotate missing row")
    class RotateMissingTests {

        @Test
        @DisplayName("rotate with no DB row throws exception")
        void rotate_missingRow_throws() {
            when(jwtUtil.isValidRefreshToken(REFRESH_TOKEN)).thenReturn(true);
            when(refreshTokenRepository.findForUpdate(JTI)).thenReturn(null);

            assertThrows(InvalidRefreshTokenException.class,
                    () -> refreshTokenService.rotate(REFRESH_TOKEN));
        }
    }

    // ---- Rotate invalid signature tests ----

    @Nested
    @DisplayName("Rotate invalid signature")
    class RotateInvalidTests {

        @Test
        @DisplayName("invalid signature throws without DB lookup")
        void invalidSignature_throwsWithoutDbLookup() {
            when(jwtUtil.isValidRefreshToken(REFRESH_TOKEN)).thenReturn(false);

            assertThrows(InvalidRefreshTokenException.class,
                    () -> refreshTokenService.rotate(REFRESH_TOKEN));

            verify(refreshTokenRepository, never()).findForUpdate(any());
        }

        @Test
        @DisplayName("null token throws without DB lookup")
        void nullToken_throwsWithoutDbLookup() {
            when(jwtUtil.isValidRefreshToken(null)).thenReturn(false);

            assertThrows(InvalidRefreshTokenException.class,
                    () -> refreshTokenService.rotate(null));

            verify(refreshTokenRepository, never()).findForUpdate(any());
        }
    }

    // ---- Rotate expired tests ----

    @Nested
    @DisplayName("Rotate expired token")
    class RotateExpiredTests {

        @Test
        @DisplayName("rotate expired token throws exception")
        void rotate_expired_throws() {
            RefreshToken expiredToken = new RefreshToken(JTI, USER_ID, FAMILY_ID, PAST);

            when(jwtUtil.isValidRefreshToken(REFRESH_TOKEN)).thenReturn(true);
            when(refreshTokenRepository.findForUpdate(JTI)).thenReturn(expiredToken);

            assertThrows(InvalidRefreshTokenException.class,
                    () -> refreshTokenService.rotate(REFRESH_TOKEN));
        }
    }

    // ---- Revoke all tests ----

    @Nested
    @DisplayName("Revoke all")
    class RevokeAllTests {

        @Test
        @DisplayName("revokeAll calls revokeAllForUser")
        void revokeAll_callsRevokeAllForUser() {
            refreshTokenService.revokeAll(USER_ID);

            verify(refreshTokenRepository).revokeAllForUser(eq(USER_ID), any(Instant.class));
        }
    }

    // ---- Revoke by token tests ----

    @Nested
    @DisplayName("Revoke by token")
    class RevokeByTokenTests {

        @Test
        @DisplayName("revokeByToken with invalid token is no-op")
        void revokeByToken_invalidToken_isNoOp() {
            when(jwtUtil.isValidRefreshToken("bad-token")).thenReturn(false);

            refreshTokenService.revokeByToken("bad-token");

            verify(refreshTokenRepository, never()).revokeFamily(any(), any());
        }

        @Test
        @DisplayName("revokeByToken with null token is no-op")
        void revokeByToken_nullToken_isNoOp() {
            refreshTokenService.revokeByToken(null);

            verify(refreshTokenRepository, never()).revokeFamily(any(), any());
        }

        @Test
        @DisplayName("revokeByToken with valid token revokes the family")
        void revokeByToken_validToken_revokesFamily() {
            RefreshToken token = new RefreshToken(JTI, USER_ID, FAMILY_ID, FUTURE);

            when(jwtUtil.isValidRefreshToken(REFRESH_TOKEN)).thenReturn(true);
            when(refreshTokenRepository.findForUpdate(JTI)).thenReturn(token);

            refreshTokenService.revokeByToken(REFRESH_TOKEN);

            verify(refreshTokenRepository).revokeFamily(eq(FAMILY_ID), any(Instant.class));
        }

        @Test
        @DisplayName("revokeByToken with missing row is no-op")
        void revokeByToken_missingRow_isNoOp() {
            when(jwtUtil.isValidRefreshToken(REFRESH_TOKEN)).thenReturn(true);
            when(refreshTokenRepository.findForUpdate(JTI)).thenReturn(null);

            refreshTokenService.revokeByToken(REFRESH_TOKEN);

            verify(refreshTokenRepository, never()).revokeFamily(any(), any());
        }
    }

    // ---- Reuse detection annotation test ----

    @Test
    @DisplayName("rotate has noRollbackFor = InvalidRefreshTokenException")
    void rotate_hasNoRollbackFor() throws NoSuchMethodException {
        Annotation annotation =
                RefreshTokenService.class.getMethod("rotate", String.class)
                        .getAnnotation(Transactional.class);
        assertThat(annotation).isNotNull();
        assertThat(((Transactional) annotation).noRollbackFor())
                .contains(InvalidRefreshTokenException.class);
    }
}
