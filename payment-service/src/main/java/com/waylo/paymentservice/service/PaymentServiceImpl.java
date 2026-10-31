package com.waylo.paymentservice.service;

import com.waylo.paymentservice.dto.PaymentMapper;
import com.waylo.paymentservice.dto.PaymentResponse;
import com.waylo.paymentservice.exception.ResourceNotFoundException;
import com.waylo.paymentservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository repository;
    private final PaymentMapper mapper;

    public PaymentServiceImpl(PaymentRepository repository, PaymentMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public PaymentResponse getById(UUID id) {
        return mapper.toResponse(repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment", "id", id)));
    }

    @Override
    public List<PaymentResponse> getAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    public List<PaymentResponse> getByBookingId(UUID bookingId) {
        return repository.findByBookingId(bookingId).stream().map(mapper::toResponse).toList();
    }

    @Override
    public List<PaymentResponse> getByCustomerId(UUID customerId) {
        return repository.findByCustomerId(customerId).stream().map(mapper::toResponse).toList();
    }
}
