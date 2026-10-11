package com.waylo.paymentservice.service;

import com.waylo.paymentservice.domain.PaymentStatus;
import com.waylo.paymentservice.dto.PaymentRequest;
import com.waylo.paymentservice.dto.PaymentResponse;

import java.util.List;
import java.util.UUID;

public interface PaymentService {
    PaymentResponse create(PaymentRequest request);
    PaymentResponse getById(UUID id);
    List<PaymentResponse> getAll();
    List<PaymentResponse> getByBookingId(UUID bookingId);
    List<PaymentResponse> getByCustomerId(UUID customerId);
    PaymentResponse refund(UUID id);
    PaymentResponse updateStatus(UUID id, PaymentStatus status);
}
