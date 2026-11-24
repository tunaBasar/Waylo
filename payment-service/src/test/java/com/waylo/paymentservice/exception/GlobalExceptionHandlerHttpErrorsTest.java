package com.waylo.paymentservice.exception;

import com.waylo.paymentservice.dto.ErrorResponse;
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
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;
import org.springframework.core.MethodParameter;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerHttpErrorsTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payments");

    @Test
    void methodNotAllowed_handler_returns405() {
        var ex = new HttpRequestMethodNotSupportedException("PATCH");

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotAllowed(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().status()).isEqualTo(405);
        assertThat(response.getBody().error()).isEqualTo("METHOD_NOT_ALLOWED");
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
    void missingParameter_handler_returnsBadRequest() {
        var ex = new MissingServletRequestParameterException("amount", "BigDecimal");

        ResponseEntity<ErrorResponse> response = handler.handleMissingParameter(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void typeMismatch_handler_returnsBadRequest() {
        var ex = new MethodArgumentTypeMismatchException("abc", String.class, "amount", null, null);

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void noResourceFound_handler_returns404() {
        var ex = new NoResourceFoundException(
                HttpMethod.GET, "/api/payments/999", "/api/payments/999");
        var req = new MockHttpServletRequest("GET", "/api/payments/999");

        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("NOT_FOUND");
    }

    @Test
    void dataIntegrityViolation_handler_returnsConflict() {
        var ex = new DataIntegrityViolationException("Duplicate entry");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("CONFLICT");
    }

    @Test
    void commissionRateConflict_handler_returns409CommissionRateExists() {
        var ex = new com.waylo.paymentservice.exception.CommissionRateConflictException(
                "A commission rate for HOTEL (default) on 2024-01-01T00:00:00Z already exists");
        var req = new MockHttpServletRequest("POST", "/api/admin/commission-rates");

        ResponseEntity<ErrorResponse> response = handler.handleCommissionRateConflict(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("COMMISSION_RATE_EXISTS");
    }

    @Test
    void optimisticLock_handler_returnsConflict() {
        var ex = new ObjectOptimisticLockingFailureException(
                com.waylo.paymentservice.domain.LedgerAccount.class, UUID.randomUUID());

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("CONCURRENT_MODIFICATION");
    }

    @Test
    void missingHeader_handler_returnsUnauthorized() {
        var ex = new MissingRequestHeaderException("X-User-Id", null);

        ResponseEntity<ErrorResponse> response = handler.handleMissingHeader(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().status()).isEqualTo(401);
        assertThat(response.getBody().error()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void genericException_handler_returns500() {
        var ex = new RuntimeException("Something went wrong");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().error()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    void conflictException_is409_withItsCodeAndDetails() {
        var ex = new ConflictException("INVALID_STATE_TRANSITION", "nope", java.util.Map.of("from", "PAID", "to", "FAILED"));
        ResponseEntity<ErrorResponse> response = handler.handleConflict(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error()).isEqualTo("INVALID_STATE_TRANSITION");
        assertThat(response.getBody().details()).containsEntry("from", "PAID").containsEntry("to", "FAILED");
    }

    @Test
    void businessRule_422_carriesDetails_andBadRequestIs400() {
        var rule = new BusinessRuleException("PAYOUT_BELOW_MINIMUM", "low", java.util.Map.of("minimum", new java.math.BigDecimal("100.00")));
        ResponseEntity<ErrorResponse> r422 = handler.handleBusinessRule(rule, request);
        assertThat(r422.getStatusCode().value()).isEqualTo(422);
        assertThat(r422.getBody().details()).containsKey("minimum");
        ResponseEntity<ErrorResponse> r400 = handler.handleBadRequest(new BadRequestException("INVALID_IBAN", "bad"), request);
        assertThat(r400.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r400.getBody().error()).isEqualTo("INVALID_IBAN");
    }

    @Test
    void dataIntegrityViolation_onAPayoutConstraint_isTranslated_withoutLeakingSql() {
        var cause = new RuntimeException("ERROR: duplicate key value violates unique constraint \"uq_payout_open\" Detail: Key (organization_id, currency)=(x, TRY)");
        ResponseEntity<ErrorResponse> r = handler.handleDataIntegrityViolation(new DataIntegrityViolationException("could not execute statement", cause), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().error()).isEqualTo("OPEN_PAYOUT_EXISTS");
        assertThat(r.getBody().message()).doesNotContain("uq_payout_open").doesNotContain("Key (");
        ResponseEntity<ErrorResponse> ref = handler.handleDataIntegrityViolation(new DataIntegrityViolationException("x", new RuntimeException("violates unique constraint \"uq_payout_reference\"")), request);
        assertThat(ref.getBody().error()).isEqualTo("PAYMENT_REFERENCE_IN_USE");
        ResponseEntity<ErrorResponse> iban = handler.handleDataIntegrityViolation(new DataIntegrityViolationException("x", new RuntimeException("violates unique constraint \"uq_bank_iban_hash_live\"")), request);
        assertThat(iban.getBody().error()).isEqualTo("IBAN_ALREADY_IN_USE");
    }
}
