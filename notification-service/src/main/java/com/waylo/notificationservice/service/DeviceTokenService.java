package com.waylo.notificationservice.service;

import com.waylo.notificationservice.domain.DeviceToken;
import com.waylo.notificationservice.dto.DeviceTokenRequest;
import com.waylo.notificationservice.dto.DeviceTokenResponse;
import com.waylo.notificationservice.exception.ResourceNotFoundException;
import com.waylo.notificationservice.repository.DeviceTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DeviceTokenService {

    private final DeviceTokenRepository repository;

    public DeviceTokenService(DeviceTokenRepository repository) { this.repository = repository; }

    /** Upsert per user + token. A token identifies one physical device, so rows of other users with it are dropped. */
    public DeviceTokenResponse register(UUID userId, DeviceTokenRequest request) {
        repository.deleteAll(repository.findByTokenAndUserIdNot(request.token(), userId));
        DeviceToken row = repository.findByUserIdAndToken(userId, request.token())
                .orElseGet(() -> new DeviceToken(userId, request.token(), request.platform()));
        row.setPlatform(request.platform());
        return toResponse(repository.save(row));
    }

    @Transactional(readOnly = true)
    public List<DeviceTokenResponse> list(UUID userId) {
        return repository.findByUserId(userId).stream().map(DeviceTokenService::toResponse).toList();
    }

    /** Only the caller's own row can be removed; a foreign or unknown token answers 404. */
    public void unregister(UUID userId, String token) {
        DeviceToken row = repository.findByUserIdAndToken(userId, token)
                .orElseThrow(() -> new ResourceNotFoundException("Device", "token", token));
        repository.delete(row);
    }

    private static DeviceTokenResponse toResponse(DeviceToken d) {
        return new DeviceTokenResponse(d.getToken(), d.getPlatform(), d.getCreatedAt());
    }
}
