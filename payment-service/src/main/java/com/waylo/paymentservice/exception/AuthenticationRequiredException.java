package com.waylo.paymentservice.exception;

public class AuthenticationRequiredException extends RuntimeException {
    public AuthenticationRequiredException(String message) { super(message); }
}
