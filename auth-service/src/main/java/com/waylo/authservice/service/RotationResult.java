package com.waylo.authservice.service;

import java.util.UUID;

/**
 * Result of a successful refresh token rotation: the caller's userId and the
 * brand-new refresh token issued in its place.
 */
public record RotationResult(UUID userId, String newRefreshToken) {
}
