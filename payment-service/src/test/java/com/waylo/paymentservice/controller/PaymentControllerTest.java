package com.waylo.paymentservice.controller;

import com.waylo.paymentservice.domain.PaymentMethod;
import com.waylo.paymentservice.domain.PaymentStatus;
import com.waylo.paymentservice.dto.ApiResponse;
import com.waylo.paymentservice.dto.PaymentRequest;
import com.waylo.paymentservice.dto.PaymentResponse;
import com.waylo.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;
    @InjectMocks
    private PaymentController controller;

    private final UUID paymentId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    private PaymentResponse buildResponse() {
        return new PaymentResponse(paymentId, bookingId, customerId, new BigDecimal("100.00"), "TRY",
                PaymentMethod.MOCK, PaymentStatus.COMPLETED, "MOCK-abc123", Instant.now(), Instant.now());
    }

    @Test
    void create_returns201() {
        PaymentRequest request = new PaymentRequest(bookingId, customerId, new BigDecimal("100.00"), "TRY", null);
        PaymentResponse response = buildResponse();
        when(paymentService.create(any(PaymentRequest.class))).thenReturn(response);

        ResponseEntity<ApiResponse<PaymentResponse>> result = controller.create(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getById_returns200() {
        PaymentResponse response = buildResponse();
        when(paymentService.getById(paymentId)).thenReturn(response);

        ResponseEntity<ApiResponse<PaymentResponse>> result = controller.getById(paymentId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getAll_returns200() {
        when(paymentService.getAll()).thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<PaymentResponse>>> result = controller.getAll();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).hasSize(1);
    }

    @Test
    void getByBookingId_returns200() {
        when(paymentService.getByBookingId(bookingId)).thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<PaymentResponse>>> result = controller.getByBookingId(bookingId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(paymentService).getByBookingId(bookingId);
    }

    @Test
    void getByCustomerId_returns200() {
        when(paymentService.getByCustomerId(customerId)).thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<PaymentResponse>>> result = controller.getByCustomerId(customerId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(paymentService).getByCustomerId(customerId);
    }

    @Test
    void refund_returns200() {
        PaymentResponse refunded = new PaymentResponse(paymentId, bookingId, customerId, new BigDecimal("100.00"),
                "TRY", PaymentMethod.MOCK, PaymentStatus.REFUNDED, "MOCK-abc123", Instant.now(), Instant.now());
        when(paymentService.refund(paymentId)).thenReturn(refunded);

        ResponseEntity<ApiResponse<PaymentResponse>> result = controller.refund(paymentId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data().status()).isEqualTo(PaymentStatus.REFUNDED);
    }
}
