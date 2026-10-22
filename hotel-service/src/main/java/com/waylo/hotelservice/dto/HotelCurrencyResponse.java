package com.waylo.hotelservice.dto;

/**
 * Currency used by the hotel across all daily rates.
 * Null if no rates exist yet.
 */
public record HotelCurrencyResponse(String currency) {}
