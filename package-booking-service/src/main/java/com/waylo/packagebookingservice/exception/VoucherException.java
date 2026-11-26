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

    private Instant validFrom;
    private Instant validUntil;

    /** Adds the voucher validity window to the error body (NOT_YET_VALID / EXPIRED). */
    public VoucherException withWindow(Instant validFrom, Instant validUntil) {
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        return this;
    }

    public Instant getValidFrom() { return validFrom; }
    public Instant getValidUntil() { return validUntil; }
    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
    public Instant getRedeemedAt() { return redeemedAt; }
}
