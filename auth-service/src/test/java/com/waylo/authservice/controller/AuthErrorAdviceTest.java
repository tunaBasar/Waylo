package com.waylo.authservice.controller;

import com.waylo.authservice.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class AuthErrorAdviceTest {

    private final AuthErrorAdvice advice = new AuthErrorAdvice();

    @Test
    void handleMissingHeader_returns401() throws Exception {
        MethodParameter methodParameter = new MethodParameter(
                AuthErrorAdviceTest.class.getDeclaredMethod("dummy", String.class), 0);
        org.springframework.web.bind.MissingRequestHeaderException ex =
                new org.springframework.web.bind.MissingRequestHeaderException("X-User-Id", methodParameter, false);
        ResponseEntity<ApiResponse<Void>> resp = advice.handleMissingHeader(ex);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertFalse(resp.getBody().success());
        assertEquals("Authentication required", resp.getBody().message());
    }

    private void dummy(String s) { }

    @Test
    void handleTypeMismatch_returns400() throws Exception {
        MethodParameter methodParameter = new MethodParameter(
                AuthErrorAdviceTest.class.getDeclaredMethod("dummy", String.class), 0);
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("value", String.class, "param", methodParameter, null);
        ResponseEntity<ApiResponse<Void>> resp = advice.handleTypeMismatch(ex);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertFalse(resp.getBody().success());
        assertEquals("Invalid request", resp.getBody().message());
    }

    @Test
    void handleResponseStatus_preservesStatusAndReason() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        ResponseEntity<ApiResponse<Void>> resp = advice.handleResponseStatus(ex);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertFalse(resp.getBody().success());
        assertEquals("User not found", resp.getBody().message());
    }

    @Test
    void handleMethodArgumentNotValid_returns400WithFieldMessages() throws Exception {
        MethodParameter methodParameter = new MethodParameter(
                AuthErrorAdviceTest.class.getDeclaredMethod("dummy", String.class), 0);
        BindException bindResult = new BindException(new Object(), "target");
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(methodParameter, bindResult);
        ResponseEntity<ApiResponse<Void>> resp = advice.handleMethodArgumentNotValid(ex);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertFalse(resp.getBody().success());
        assertNotNull(resp.getBody().message());
    }
}
