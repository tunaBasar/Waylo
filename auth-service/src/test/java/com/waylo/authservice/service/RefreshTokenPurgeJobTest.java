package com.waylo.authservice.service;

import com.waylo.authservice.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefreshTokenPurgeJobTest {
    @Test
    void purge_usesNowAndSevenDayCutoff() {
        RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
        when(repo.purgeExpiredOrOldRevoked(any(), any())).thenReturn(3);
        Instant before = Instant.now();
        new RefreshTokenPurgeJob(repo).purge();
        Instant after = Instant.now();
        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repo).purgeExpiredOrOldRevoked(now.capture(), cutoff.capture());
        assertFalse(now.getValue().isBefore(before));
        assertFalse(now.getValue().isAfter(after));
        assertEquals(Duration.ofDays(7), Duration.between(cutoff.getValue(), now.getValue()));
    }
}
