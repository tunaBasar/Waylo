package com.waylo.notificationservice.dto;

import java.time.Instant;

public record DeviceTokenResponse(String token, String platform, Instant createdAt) {}
