package com.waylo.packagebookingservice.exception;

/** Hotel lead guest name failed validation (400 GUEST_NAME_INVALID). */
public class GuestNameInvalidException extends RuntimeException {
    public GuestNameInvalidException(String message) { super(message); }
}
