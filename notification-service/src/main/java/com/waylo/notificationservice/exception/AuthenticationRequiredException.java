package com.waylo.notificationservice.exception;

public class AuthenticationRequiredException extends RuntimeException {
    public AuthenticationRequiredException(String message) { super(message); }
}
