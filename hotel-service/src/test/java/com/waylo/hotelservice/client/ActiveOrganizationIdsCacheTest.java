package com.waylo.hotelservice.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActiveOrganizationIdsCacheTest {

    private final AuthAccessClient client = mock(AuthAccessClient.class);
    private final UUID org = UUID.randomUUID();

    @Test
    void secondCallWithinTtlIsServedFromCache() {
        when(client.activeOrganizationIds()).thenReturn(List.of(org));
        ActiveOrganizationIdsCache cache = new ActiveOrganizationIdsCache(client, 60);

        assertThat(cache.get()).containsExactly(org);
        assertThat(cache.get()).containsExactly(org);

        verify(client, times(1)).activeOrganizationIds();
    }

    @Test
    void ttlZeroDisablesCaching() {
        when(client.activeOrganizationIds()).thenReturn(List.of(org));
        ActiveOrganizationIdsCache cache = new ActiveOrganizationIdsCache(client, 0);

        cache.get();
        cache.get();

        verify(client, times(2)).activeOrganizationIds();
    }

    @Test
    void missWhileAuthDownFailsClosedAndFailureIsNotCached() {
        when(client.activeOrganizationIds()).thenThrow(new RuntimeException("auth down"));
        ActiveOrganizationIdsCache cache = new ActiveOrganizationIdsCache(client, 60);

        assertThatThrownBy(cache::get).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(cache::get).isInstanceOf(RuntimeException.class);

        verify(client, times(2)).activeOrganizationIds();
    }
}
