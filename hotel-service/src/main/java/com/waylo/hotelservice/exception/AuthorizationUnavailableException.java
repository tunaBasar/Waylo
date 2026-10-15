package com.waylo.hotelservice.exception;

public class AuthorizationUnavailableException extends RuntimeException {

    public AuthorizationUnavailableException(String message) {
        super(message);
    }
}
