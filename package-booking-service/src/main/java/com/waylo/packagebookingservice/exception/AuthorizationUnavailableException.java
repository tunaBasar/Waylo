package com.waylo.packagebookingservice.exception;

public class AuthorizationUnavailableException extends RuntimeException {
    public AuthorizationUnavailableException(String message) {
        super(message);
    }
}
