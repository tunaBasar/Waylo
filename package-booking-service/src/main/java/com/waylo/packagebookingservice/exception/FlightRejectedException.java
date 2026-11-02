package com.waylo.packagebookingservice.exception;

public class FlightRejectedException extends RuntimeException {
    private final int flightStatus;
    private final String code;

    public FlightRejectedException(int flightStatus, String code, String message) {
        super(message);
        this.flightStatus = flightStatus;
        this.code = code;
    }

    public int getFlightStatus() { return flightStatus; }
    public String getCode() { return code; }
}
