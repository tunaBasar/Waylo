package com.waylo.flightservice.exception;

import com.waylo.flightservice.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/flights");

    @Test
    void optimisticLockingFailure_handler_returnsConflict() {
        var ex = new ObjectOptimisticLockingFailureException(
                com.waylo.flightservice.domain.Flight.class, UUID.randomUUID());

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(response.getBody().path()).isEqualTo("/api/flights");
    }

    @Test
    void dataIntegrityViolation_handler_returnsConflict_withoutSecret() {
        var ex = new DataIntegrityViolationException("Duplicate entry 'secret_sql'");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("CONFLICT");
        assertThat(response.getBody().message()).doesNotContain("secret_sql");
    }

    @Test
    void missingParameter_handler_returnsBadRequest() {
        var ex = new MissingServletRequestParameterException("from", "String");

        ResponseEntity<ErrorResponse> response = handler.handleMissingParameter(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().message()).contains("Missing required parameter: from");
    }

    @Test
    void messageNotReadable_handler_returnsBadRequest() {
        var ex = new HttpMessageNotReadableException("bad", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ErrorResponse> response = handler.handleMessageNotReadable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void methodNotAllowed_handler_returns405() {
        var ex = new HttpRequestMethodNotSupportedException("PATCH");

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotAllowed(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().status()).isEqualTo(405);
        assertThat(response.getBody().error()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void noResourceFound_handler_returns404() {
        var ex = new NoResourceFoundException(
                HttpMethod.GET, "/api/flights/999", "/api/flights/999");
        var req = new MockHttpServletRequest("GET", "/api/flights/999");

        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("NOT_FOUND");
    }

    @Test
    void genericException_handler_returns500() {
        var ex = new RuntimeException("Something went wrong");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().error()).isEqualTo("INTERNAL_SERVER_ERROR");
    }
}
