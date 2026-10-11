package com.waylo.packagebookingsagaorchestrator.service;

import com.waylo.packagebookingsagaorchestrator.domain.SagaInstance;
import com.waylo.packagebookingsagaorchestrator.domain.SagaStatus;
import com.waylo.packagebookingsagaorchestrator.domain.SagaStep;
import com.waylo.packagebookingsagaorchestrator.domain.SagaStepStatus;
import com.waylo.packagebookingsagaorchestrator.domain.SagaStepType;
import com.waylo.packagebookingsagaorchestrator.dto.SagaInstanceResponse;
import com.waylo.packagebookingsagaorchestrator.dto.SagaMapper;
import com.waylo.packagebookingsagaorchestrator.dto.SagaStartRequest;
import com.waylo.packagebookingsagaorchestrator.exception.DuplicateResourceException;
import com.waylo.packagebookingsagaorchestrator.exception.ResourceNotFoundException;
import com.waylo.packagebookingsagaorchestrator.repository.SagaInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SagaOrchestratorServiceImpl implements SagaOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestratorServiceImpl.class);

    private final SagaInstanceRepository repository;
    private final SagaMapper mapper;

    public SagaOrchestratorServiceImpl(SagaInstanceRepository repository, SagaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public SagaInstanceResponse startSaga(SagaStartRequest request) {
        if (repository.existsByBookingId(request.bookingId())) {
            throw new DuplicateResourceException("SagaInstance", "bookingId", request.bookingId());
        }

        SagaInstance saga = new SagaInstance();
        saga.setBookingId(request.bookingId());
        saga.setCustomerId(request.customerId());
        saga.setStatus(SagaStatus.STARTED);

        int order = 0;
        if (request.flightId() != null) {
            saga.addStep(createStep(SagaStepType.RESERVE_FLIGHT, order++));
        }
        if (request.hotelReservationId() != null) {
            saga.addStep(createStep(SagaStepType.RESERVE_HOTEL, order++));
        }
        if (request.vehicleReservationId() != null) {
            saga.addStep(createStep(SagaStepType.RESERVE_CAR, order++));
        }
        saga.addStep(createStep(SagaStepType.PROCESS_PAYMENT, order++));
        saga.addStep(createStep(SagaStepType.CONFIRM_BOOKING, order));

        saga.setCurrentStep(saga.getSteps().getFirst().getStepType().name());
        saga = repository.save(saga);
        log.info("Saga started: id={}, bookingId={}, steps={}", saga.getId(), saga.getBookingId(), saga.getSteps().size());
        return mapper.toResponse(saga);
    }

    @Override @Transactional(readOnly = true)
    public SagaInstanceResponse getById(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Override @Transactional(readOnly = true)
    public SagaInstanceResponse getByBookingId(UUID bookingId) {
        SagaInstance saga = repository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("SagaInstance", "bookingId", bookingId));
        return mapper.toResponse(saga);
    }

    @Override @Transactional(readOnly = true)
    public List<SagaInstanceResponse> getAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<SagaInstanceResponse> getByStatus(SagaStatus status) {
        return repository.findByStatus(status).stream().map(mapper::toResponse).toList();
    }

    @Override
    public SagaInstanceResponse compensate(UUID id) {
        SagaInstance saga = findOrThrow(id);
        saga.setStatus(SagaStatus.COMPENSATING);
        log.info("Saga compensation started: id={}", id);
        // Mark completed steps for compensation
        for (SagaStep step : saga.getSteps()) {
            if (step.getStatus() == SagaStepStatus.COMPLETED) {
                step.setStatus(SagaStepStatus.COMPENSATED);
            }
        }
        saga.setStatus(SagaStatus.COMPENSATED);
        saga = repository.save(saga);
        return mapper.toResponse(saga);
    }

    private SagaStep createStep(SagaStepType type, int order) {
        SagaStep step = new SagaStep();
        step.setStepType(type);
        step.setStatus(SagaStepStatus.PENDING);
        step.setStepOrder(order);
        return step;
    }

    private SagaInstance findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("SagaInstance", "id", id));
    }
}
