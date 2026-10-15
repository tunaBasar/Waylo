package com.waylo.hotelservice.exception;

import com.waylo.hotelservice.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleResourceNotFound_returns404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Hotel", "id", "123");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/hotels/123");

        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().path()).isEqualTo("/api/hotels/123");
    }

    @Test
    void handleDuplicateResource_returns409() {
        DuplicateResourceException ex = new DuplicateResourceException("Room", "roomNumber", "101");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/hotels/1/rooms");

        ResponseEntity<ErrorResponse> response = handler.handleDuplicateResource(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("CONFLICT");
    }

    @Test
    void handleBusinessRule_returns422() {
        BusinessRuleException ex = new BusinessRuleException("INVALID_CAPACITY", "Capacity must be positive");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/hotels/1/rooms");

        ResponseEntity<ErrorResponse> response = handler.handleBusinessRule(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("INVALID_CAPACITY");
    }

    @Test
    void handleValidation_returns400() {
        org.springframework.validation.FieldError fieldError = mock(org.springframework.validation.FieldError.class);
        when(fieldError.getField()).thenReturn("name");
        when(fieldError.getDefaultMessage()).thenReturn("must not be blank");
        org.springframework.validation.BeanPropertyBindingResult bindingResult =
                new org.springframework.validation.BeanPropertyBindingResult(null, "hotel");
        bindingResult.addError(fieldError);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/hotels");

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).contains("name");
    }

    @Test
    void handleIllegalArgument_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid value");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/hotels");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void handleTypeMismatch_returns400() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                null, Integer.class, "page", null, null);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/hotels");

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("TYPE_MISMATCH");
    }

    @Test
    void handleMissingParameter_returns400() {
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException(
                "sort", "String");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/hotels");

        ResponseEntity<ErrorResponse> response = handler.handleMissingParameter(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("MISSING_PARAMETER");
    }

    @Test
    void handleMalformedRequest_returns400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("No content", null);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/hotels");

        ResponseEntity<ErrorResponse> response = handler.handleMalformedRequest(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void handleDataIntegrityViolation_returns409() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Duplicate");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/hotels");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("DATA_INTEGRITY_VIOLATION");
    }

    @Test
    void handleOptimisticLocking_returns409() {
        ObjectOptimisticLockingFailureException ex = new ObjectOptimisticLockingFailureException(
                Object.class, new Object());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/hotels");

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLocking(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("CONCURRENT_MODIFICATION");
    }

    @Test
    void handleMethodNotAllowed_returns405() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("DELETE");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/hotels");

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotAllowed(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void handleGeneric_returns500() {
        Exception ex = new RuntimeException("Something went wrong");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/hotels");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("INTERNAL_SERVER_ERROR");
    }
}
