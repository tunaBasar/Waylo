package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.domain.BookingStatus;
import com.waylo.packagebookingservice.domain.PackageBooking;
import com.waylo.packagebookingservice.dto.PackageBookingMapper;
import com.waylo.packagebookingservice.dto.PackageBookingRequest;
import com.waylo.packagebookingservice.dto.PackageBookingResponse;
import com.waylo.packagebookingservice.event.BookingCancelledEvent;
import com.waylo.packagebookingservice.event.BookingCreatedEvent;
import com.waylo.packagebookingservice.event.BookingEventPublisher;
import com.waylo.packagebookingservice.exception.ResourceNotFoundException;
import com.waylo.packagebookingservice.repository.PackageBookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PackageBookingServiceImpl implements PackageBookingService {

    private final PackageBookingRepository bookingRepository;
    private final PackageBookingMapper bookingMapper;
    private final BookingEventPublisher eventPublisher;

    public PackageBookingServiceImpl(PackageBookingRepository bookingRepository,
                                     PackageBookingMapper bookingMapper,
                                     BookingEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.bookingMapper = bookingMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public PackageBookingResponse create(PackageBookingRequest request) {
        PackageBooking booking = bookingMapper.toEntity(request);
        booking.setStatus(BookingStatus.PENDING);
        booking = bookingRepository.save(booking);
        eventPublisher.publishBookingCreated(new BookingCreatedEvent(
                booking.getId(), booking.getCustomerId(), booking.getFlightId(),
                booking.getHotelReservationId(), booking.getVehicleReservationId(), booking.getTotalPrice()));
        return bookingMapper.toResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public PackageBookingResponse getById(UUID id) {
        return bookingMapper.toResponse(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackageBookingResponse> getAll() {
        return bookingRepository.findAll().stream().map(bookingMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackageBookingResponse> getByCustomerId(UUID customerId) {
        return bookingRepository.findByCustomerId(customerId).stream()
                .map(bookingMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackageBookingResponse> getByFlightId(UUID flightId) {
        return bookingRepository.findByFlightId(flightId).stream()
                .map(bookingMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackageBookingResponse> search(UUID customerId, BookingStatus status) {
        if (customerId != null && status != null) {
            return bookingRepository.findByCustomerIdAndStatus(customerId, status).stream()
                    .map(bookingMapper::toResponse).toList();
        } else if (customerId != null) {
            return bookingRepository.findByCustomerId(customerId).stream()
                    .map(bookingMapper::toResponse).toList();
        } else if (status != null) {
            return bookingRepository.findByStatus(status).stream()
                    .map(bookingMapper::toResponse).toList();
        }
        return getAll();
    }

    @Override
    public PackageBookingResponse update(UUID id, PackageBookingRequest request) {
        PackageBooking booking = findOrThrow(id);
        bookingMapper.updateEntity(request, booking);
        booking = bookingRepository.save(booking);
        return bookingMapper.toResponse(booking);
    }

    @Override
    public void delete(UUID id) {
        if (!bookingRepository.existsById(id)) {
            throw new ResourceNotFoundException("PackageBooking", "id", id);
        }
        bookingRepository.deleteById(id);
    }

    @Override
    public PackageBookingResponse updateStatus(UUID id, BookingStatus status) {
        PackageBooking booking = findOrThrow(id);
        booking.setStatus(status);
        booking = bookingRepository.save(booking);
        return bookingMapper.toResponse(booking);
    }

    @Override
    public PackageBookingResponse cancel(UUID id) {
        PackageBooking booking = findOrThrow(id);
        booking.setStatus(BookingStatus.CANCELLED);
        booking = bookingRepository.save(booking);
        eventPublisher.publishBookingCancelled(new BookingCancelledEvent(booking.getId(), booking.getCustomerId()));
        return bookingMapper.toResponse(booking);
    }

    private PackageBooking findOrThrow(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PackageBooking", "id", id));
    }
}
