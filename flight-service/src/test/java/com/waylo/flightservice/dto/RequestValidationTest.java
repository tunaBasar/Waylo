package com.waylo.flightservice.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setupValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    // ==================== FlightRequest ====================

    @Test
    void validFlightRequest_hasZeroViolations() {
        FlightRequest request = new FlightRequest(
                "WA100", "IST", "JFK", "Waylo Air",
                Instant.parse("2026-12-01T07:00:00Z"),
                Instant.parse("2026-12-01T09:00:00Z"));

        Set<ConstraintViolation<FlightRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void nullDepartureTime_flightRequestHasViolation() {
        FlightRequest request = new FlightRequest(
                "WA100", "IST", "JFK", "Waylo Air",
                null,
                Instant.parse("2026-12-01T09:00:00Z"));

        Set<ConstraintViolation<FlightRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                "departureTime".equals(v.getPropertyPath().toString())
                        && v.getMessage().contains("required"));
    }

    @Test
    void nullArrivalTime_flightRequestHasViolation() {
        FlightRequest request = new FlightRequest(
                "WA100", "IST", "JFK", "Waylo Air",
                Instant.parse("2026-12-01T07:00:00Z"),
                null);

        Set<ConstraintViolation<FlightRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                "arrivalTime".equals(v.getPropertyPath().toString())
                        && v.getMessage().contains("required"));
    }

    @Test
    void badIataCode_flightRequestHasViolation() {
        FlightRequest request = new FlightRequest(
                "WA100", "I1T", "JFK", "Waylo Air",
                Instant.parse("2026-12-01T07:00:00Z"),
                Instant.parse("2026-12-01T09:00:00Z"));

        Set<ConstraintViolation<FlightRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                "departureAirportCode".equals(v.getPropertyPath().toString())
                        && v.getMessage().contains("3 letters"));
    }

    // ==================== AirportRequest ====================

    @Test
    void validAirportRequest_hasZeroViolations() {
        AirportRequest request = new AirportRequest(
                "IST", "Istanbul Airport", "Istanbul", "Turkey",
                "Europe/Istanbul", BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0));

        Set<ConstraintViolation<AirportRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void latitude91_airportRequestHasViolation() {
        AirportRequest request = new AirportRequest(
                "IST", "Istanbul Airport", "Istanbul", "Turkey",
                "Europe/Istanbul", BigDecimal.valueOf(91.0), BigDecimal.valueOf(29.0));

        Set<ConstraintViolation<AirportRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                "latitude".equals(v.getPropertyPath().toString())
                        && v.getMessage().contains("-90 and 90"));
    }

    @Test
    void iataCodeISTA_airportRequestHasViolation() {
        AirportRequest request = new AirportRequest(
                "ISTA", "Istanbul Airport", "Istanbul", "Turkey",
                "Europe/Istanbul", BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0));

        Set<ConstraintViolation<AirportRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                "iataCode".equals(v.getPropertyPath().toString())
                        && v.getMessage().contains("3 letters"));
    }

    @Test
    void longitudeExceeds180_airportRequestHasViolation() {
        AirportRequest request = new AirportRequest(
                "IST", "Istanbul Airport", "Istanbul", "Turkey",
                "Europe/Istanbul", BigDecimal.valueOf(41.0), BigDecimal.valueOf(200.0));

        Set<ConstraintViolation<AirportRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                "longitude".equals(v.getPropertyPath().toString()));
    }

    @Test
    void nullIataCode_airportRequestHasViolation() {
        AirportRequest request = new AirportRequest(
                null, "Istanbul Airport", "Istanbul", "Turkey",
                "Europe/Istanbul", BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0));

        Set<ConstraintViolation<AirportRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                "iataCode".equals(v.getPropertyPath().toString())
                        && v.getMessage().contains("required"));
    }
}
