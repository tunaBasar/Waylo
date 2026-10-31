package com.waylo.packagebookingservice.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;

/** Redemption failure with a stable machine code. Messages never reveal more than the code. */
public class VoucherException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Instant redeemedAt;

    public VoucherException(String code, HttpStatus status) {
        this(code, status, null);
    }

    public VoucherException(String code, HttpStatus status, Instant redeemedAt) {
        super(code);
        this.code = code;
        this.status = status;
        this.redeemedAt = redeemedAt;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
    public Instant getRedeemedAt() { return redeemedAt; }
}
