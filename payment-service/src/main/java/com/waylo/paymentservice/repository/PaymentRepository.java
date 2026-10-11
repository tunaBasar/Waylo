package com.waylo.paymentservice.repository;

import com.waylo.paymentservice.domain.Payment;
import com.waylo.paymentservice.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByBookingId(UUID bookingId);
    List<Payment> findByCustomerId(UUID customerId);
    List<Payment> findByStatus(PaymentStatus status);
    Optional<Payment> findByTransactionRef(String transactionRef);
    List<Payment> findByCustomerIdAndStatus(UUID customerId, PaymentStatus status);
}
