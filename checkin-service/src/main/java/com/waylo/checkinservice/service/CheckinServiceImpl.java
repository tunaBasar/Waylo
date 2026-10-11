package com.waylo.checkinservice.service;

import com.waylo.checkinservice.domain.Checkin;
import com.waylo.checkinservice.domain.CheckinStatus;
import com.waylo.checkinservice.dto.CheckinMapper;
import com.waylo.checkinservice.dto.CheckinRequest;
import com.waylo.checkinservice.dto.CheckinResponse;
import com.waylo.checkinservice.exception.ResourceNotFoundException;
import com.waylo.checkinservice.repository.CheckinRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CheckinServiceImpl implements CheckinService {

    private final CheckinRepository repository;
    private final CheckinMapper mapper;

    public CheckinServiceImpl(CheckinRepository repository, CheckinMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public CheckinResponse checkIn(CheckinRequest request) {
        Checkin checkin = mapper.toEntity(request);
        checkin.setStatus(CheckinStatus.CHECKED_IN);
        checkin.setCheckedInAt(Instant.now());
        checkin = repository.save(checkin);
        return mapper.toResponse(checkin);
    }

    @Override @Transactional(readOnly = true)
    public CheckinResponse getById(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Override @Transactional(readOnly = true)
    public List<CheckinResponse> getAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<CheckinResponse> getByBookingId(UUID bookingId) {
        return repository.findByBookingId(bookingId).stream().map(mapper::toResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<CheckinResponse> getByFlightId(UUID flightId) {
        return repository.findByFlightId(flightId).stream().map(mapper::toResponse).toList();
    }

    @Override
    public CheckinResponse issueBoardingPass(UUID id) {
        Checkin checkin = findOrThrow(id);
        checkin.setStatus(CheckinStatus.BOARDING_PASS_ISSUED);
        checkin.setBoardingPassUrl("https://waylo.com/boarding-pass/" + id);
        checkin = repository.save(checkin);
        return mapper.toResponse(checkin);
    }

    @Override
    public CheckinResponse cancel(UUID id) {
        Checkin checkin = findOrThrow(id);
        checkin.setStatus(CheckinStatus.CANCELLED);
        checkin = repository.save(checkin);
        return mapper.toResponse(checkin);
    }

    private Checkin findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Checkin", "id", id));
    }
}
