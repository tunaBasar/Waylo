package com.waylo.authservice.service;

/**
 * Thrown when a refresh token is invalid, expired, already used, or revoked.
 * Reusing a used refresh token triggers family revocation before this exception.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }

    public InvalidRefreshTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
