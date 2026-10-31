package com.waylo.paymentservice.service;

import com.waylo.paymentservice.dto.PaymentResponse;

import java.util.List;
import java.util.UUID;

/**
 * Read-only access to the legacy mock `payments` table (history only). The mock create/refund/updateStatus operations were removed in
 * Task 7b: money now moves only through PaymentIntentService and EscrowService.
 */
public interface PaymentService {
    PaymentResponse getById(UUID id);
    List<PaymentResponse> getAll();
    List<PaymentResponse> getByBookingId(UUID bookingId);
    List<PaymentResponse> getByCustomerId(UUID customerId);
}
