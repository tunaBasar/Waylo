package com.waylo.paymentservice.service;

import com.waylo.paymentservice.domain.Payment;
import com.waylo.paymentservice.domain.PaymentStatus;
import com.waylo.paymentservice.dto.PaymentMapper;
import com.waylo.paymentservice.dto.PaymentRequest;
import com.waylo.paymentservice.dto.PaymentResponse;
import com.waylo.paymentservice.event.PaymentCompletedEvent;
import com.waylo.paymentservice.event.PaymentEventPublisher;
import com.waylo.paymentservice.event.PaymentRefundedEvent;
import com.waylo.paymentservice.exception.ResourceNotFoundException;
import com.waylo.paymentservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository repository;
    private final PaymentMapper mapper;
    private final PaymentEventPublisher eventPublisher;

    public PaymentServiceImpl(PaymentRepository repository, PaymentMapper mapper, PaymentEventPublisher eventPublisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public PaymentResponse create(PaymentRequest request) {
        Payment payment = mapper.toEntity(request);
        payment.setStatus(PaymentStatus.PENDING);
        // Mock payment: auto-complete
        payment.setTransactionRef("MOCK-" + UUID.randomUUID().toString().substring(0, 8));
        payment.setStatus(PaymentStatus.COMPLETED);
        payment = repository.save(payment);
        eventPublisher.publishPaymentCompleted(new PaymentCompletedEvent(
                payment.getId(), payment.getBookingId(), payment.getCustomerId(),
                payment.getAmount(), payment.getTransactionRef()));
        return mapper.toResponse(payment);
    }

    @Override @Transactional(readOnly = true)
    public PaymentResponse getById(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Override @Transactional(readOnly = true)
    public List<PaymentResponse> getAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<PaymentResponse> getByBookingId(UUID bookingId) {
        return repository.findByBookingId(bookingId).stream().map(mapper::toResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<PaymentResponse> getByCustomerId(UUID customerId) {
        return repository.findByCustomerId(customerId).stream().map(mapper::toResponse).toList();
    }

    @Override
    public PaymentResponse refund(UUID id) {
        Payment payment = findOrThrow(id);
        payment.setStatus(PaymentStatus.REFUNDED);
        payment = repository.save(payment);
        eventPublisher.publishPaymentRefunded(new PaymentRefundedEvent(
                payment.getId(), payment.getBookingId(), payment.getCustomerId()));
        return mapper.toResponse(payment);
    }

    @Override
    public PaymentResponse updateStatus(UUID id, PaymentStatus status) {
        Payment payment = findOrThrow(id);
        payment.setStatus(status);
        payment = repository.save(payment);
        return mapper.toResponse(payment);
    }

    private Payment findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment", "id", id));
    }
}
