package com.waylo.packagebookingservice.exception;

public class BusRejectedException extends RuntimeException {
    private final int busStatus;
    private final String code;
    public BusRejectedException(int busStatus, String code, String message) { super(message); this.busStatus = busStatus; this.code = code; }
    public int getBusStatus() { return busStatus; }
    public String getCode() { return code; }
}
