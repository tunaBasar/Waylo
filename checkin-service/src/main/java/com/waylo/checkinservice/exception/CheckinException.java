package com.waylo.checkinservice.exception;

/** Business error with an explicit HTTP status and error code (CHECKIN-CONTRACT.md section 6). */
public class CheckinException extends RuntimeException {
    private final int status;
    private final String code;

    public CheckinException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
}
