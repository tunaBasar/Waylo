package com.waylo.packagebookingservice.exception;

/** hotel-service answered with a business error (404 / 422). */
public class HotelRejectedException extends RuntimeException {

    private final int hotelStatus;
    private final String code;

    public HotelRejectedException(int hotelStatus, String code, String message) {
        super(message);
        this.hotelStatus = hotelStatus;
        this.code = code;
    }

    public int getHotelStatus() { return hotelStatus; }
    public String getCode() { return code; }
}
