package com.waylo.packagebookingservice.exception;

/** hotel-service could not be reached or failed. */
public class HotelUnavailableException extends RuntimeException {

    public HotelUnavailableException(String message) { super(message); }

    public HotelUnavailableException(String message, Throwable cause) { super(message, cause); }
}
