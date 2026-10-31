package com.waylo.authservice.dto;

public record RegisterResponse(String email, boolean emailVerified) {
}
