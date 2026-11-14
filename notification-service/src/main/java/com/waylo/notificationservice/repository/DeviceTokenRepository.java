package com.waylo.notificationservice.repository;

import com.waylo.notificationservice.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {
    List<DeviceToken> findByUserId(UUID userId);
    Optional<DeviceToken> findByUserIdAndToken(UUID userId, String token);
    List<DeviceToken> findByTokenAndUserIdNot(String token, UUID userId);
    List<DeviceToken> findByTokenIn(java.util.Collection<String> tokens);
}
