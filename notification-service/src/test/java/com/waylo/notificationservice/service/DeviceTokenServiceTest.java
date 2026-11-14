package com.waylo.notificationservice.service;

import com.waylo.notificationservice.domain.DeviceToken;
import com.waylo.notificationservice.dto.DeviceTokenRequest;
import com.waylo.notificationservice.exception.ResourceNotFoundException;
import com.waylo.notificationservice.repository.DeviceTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock DeviceTokenRepository repository;
    private final UUID user = UUID.randomUUID();

    @Test
    void register_newToken_savesRow() {
        when(repository.findByTokenAndUserIdNot("t", user)).thenReturn(List.of());
        when(repository.findByUserIdAndToken(user, "t")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        var r = new DeviceTokenService(repository).register(user, new DeviceTokenRequest("t", "ios"));
        assertThat(r.token()).isEqualTo("t");
        assertThat(r.platform()).isEqualTo("ios");
    }

    @Test
    void register_existing_updatesPlatformAndDropsOtherOwners() {
        DeviceToken existing = new DeviceToken(user, "t", "ios");
        DeviceToken foreign = new DeviceToken(UUID.randomUUID(), "t", "ios");
        when(repository.findByTokenAndUserIdNot("t", user)).thenReturn(List.of(foreign));
        when(repository.findByUserIdAndToken(user, "t")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        new DeviceTokenService(repository).register(user, new DeviceTokenRequest("t", "android"));
        assertThat(existing.getPlatform()).isEqualTo("android");
        verify(repository).deleteAll(List.of(foreign));
    }

    @Test
    void unregister_foreignOrUnknown_404() {
        when(repository.findByUserIdAndToken(user, "t")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new DeviceTokenService(repository).unregister(user, "t"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void unregister_own_deletes() {
        DeviceToken row = new DeviceToken(user, "t", "ios");
        when(repository.findByUserIdAndToken(user, "t")).thenReturn(Optional.of(row));
        new DeviceTokenService(repository).unregister(user, "t");
        verify(repository).delete(row);
    }
}
