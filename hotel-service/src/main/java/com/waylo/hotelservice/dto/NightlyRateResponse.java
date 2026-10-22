package com.waylo.hotelservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Nightly rate snapshot for availability and quote responses.
 */
public record NightlyRateResponse(
        LocalDate stayDate,
        BigDecimal amount,
        String currency
) {}
