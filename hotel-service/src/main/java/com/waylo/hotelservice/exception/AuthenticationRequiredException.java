package com.waylo.hotelservice.exception;

public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException(String message) {
        super(message);
    }
}
