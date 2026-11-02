package com.waylo.packagebookingservice.exception;

public class CarRejectedException extends RuntimeException {
    private final int carStatus;
    private final String code;
    public CarRejectedException(int carStatus, String code, String message) { super(message); this.carStatus = carStatus; this.code = code; }
    public int getCarStatus() { return carStatus; }
    public String getCode() { return code; }
}
