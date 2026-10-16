package com.waylo.paymentservice.service;

import com.waylo.paymentservice.domain.Payment;
import com.waylo.paymentservice.domain.PaymentMethod;
import com.waylo.paymentservice.domain.PaymentStatus;
import com.waylo.paymentservice.dto.PaymentMapper;
import com.waylo.paymentservice.dto.PaymentRequest;
import com.waylo.paymentservice.dto.PaymentResponse;
import com.waylo.paymentservice.event.PaymentCompletedEvent;
import com.waylo.paymentservice.event.PaymentEventPublisher;
import com.waylo.paymentservice.event.PaymentRefundedEvent;
import com.waylo.paymentservice.exception.ResourceNotFoundException;
import com.waylo.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository repository;
    @Mock
    private PaymentMapper mapper;
    @Mock
    private PaymentEventPublisher eventPublisher;

    private PaymentServiceImpl service;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    private PaymentServiceImpl newService() {
        return new PaymentServiceImpl(repository, mapper, eventPublisher);
    }

    private Payment buildPayment(UUID id, PaymentStatus status) {
        Payment p = new Payment();
        p.setId(id);
        p.setBookingId(bookingId);
        p.setCustomerId(customerId);
        p.setAmount(new BigDecimal("150.00"));
        p.setCurrency("TRY");
        p.setPaymentMethod(PaymentMethod.MOCK);
        p.setStatus(status);
        return p;
    }

    // --- create ---

    @Test
    void create_mockPaymentAutoCompletesWithTransactionRef() {
        service = newService();
        PaymentRequest request = new PaymentRequest(bookingId, customerId, new BigDecimal("150.00"), "TRY", null);
        Payment mapped = new Payment(); // no paymentMethod set -> null, service defaults to MOCK
        mapped.setBookingId(bookingId);
        mapped.setCustomerId(customerId);
        mapped.setAmount(new BigDecimal("150.00"));
        mapped.setCurrency("TRY");
        mapped.setPaymentMethod(null);

        when(mapper.toEntity(request)).thenReturn(mapped);
        when(repository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        PaymentResponse response = mock(PaymentResponse.class);
        when(mapper.toResponse(any(Payment.class))).thenReturn(response);

        PaymentResponse result = service.create(request);

        assertThat(result).isEqualTo(response);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(repository).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.MOCK);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(saved.getTransactionRef()).startsWith("MOCK-");

        verify(eventPublisher).publishPaymentCompleted(any(PaymentCompletedEvent.class));
    }

    @Test
    void create_explicitPaymentMethodPreserved() {
        service = newService();
        PaymentRequest request = new PaymentRequest(bookingId, customerId, new BigDecimal("50.00"), "USD", PaymentMethod.CREDIT_CARD);
        Payment mapped = new Payment();
        mapped.setPaymentMethod(PaymentMethod.CREDIT_CARD);

        when(mapper.toEntity(request)).thenReturn(mapped);
        when(repository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Payment.class))).thenReturn(mock(PaymentResponse.class));

        service.create(request);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
    }

    @Test
    void create_eachPaymentGetsUniqueTransactionRef() {
        service = newService();
        PaymentRequest request = new PaymentRequest(bookingId, customerId, new BigDecimal("10.00"), "TRY", null);
        when(mapper.toEntity(request)).thenAnswer(inv -> new Payment());
        when(repository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Payment.class))).thenReturn(mock(PaymentResponse.class));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        service.create(request);
        service.create(request);
        verify(repository, times(2)).save(captor.capture());

        List<Payment> saved = captor.getAllValues();
        assertThat(saved.get(0).getTransactionRef()).isNotEqualTo(saved.get(1).getTransactionRef());
    }

    // --- getById ---

    @Test
    void getById_found_returnsMappedResponse() {
        service = newService();
        UUID id = UUID.randomUUID();
        Payment payment = buildPayment(id, PaymentStatus.COMPLETED);
        PaymentResponse response = mock(PaymentResponse.class);
        when(repository.findById(id)).thenReturn(Optional.of(payment));
        when(mapper.toResponse(payment)).thenReturn(response);

        PaymentResponse result = service.getById(id);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getById_notFound_throwsResourceNotFound() {
        service = newService();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getAll / getByBookingId / getByCustomerId ---

    @Test
    void getAll_mapsAllPayments() {
        service = newService();
        Payment p1 = buildPayment(UUID.randomUUID(), PaymentStatus.COMPLETED);
        Payment p2 = buildPayment(UUID.randomUUID(), PaymentStatus.PENDING);
        when(repository.findAll()).thenReturn(List.of(p1, p2));
        when(mapper.toResponse(any(Payment.class))).thenReturn(mock(PaymentResponse.class));

        List<PaymentResponse> result = service.getAll();

        assertThat(result).hasSize(2);
    }

    @Test
    void getByBookingId_filtersCorrectly() {
        service = newService();
        Payment p = buildPayment(UUID.randomUUID(), PaymentStatus.COMPLETED);
        when(repository.findByBookingId(bookingId)).thenReturn(List.of(p));
        when(mapper.toResponse(p)).thenReturn(mock(PaymentResponse.class));

        List<PaymentResponse> result = service.getByBookingId(bookingId);

        assertThat(result).hasSize(1);
        verify(repository).findByBookingId(bookingId);
    }

    @Test
    void getByCustomerId_filtersCorrectly() {
        service = newService();
        Payment p = buildPayment(UUID.randomUUID(), PaymentStatus.COMPLETED);
        when(repository.findByCustomerId(customerId)).thenReturn(List.of(p));
        when(mapper.toResponse(p)).thenReturn(mock(PaymentResponse.class));

        List<PaymentResponse> result = service.getByCustomerId(customerId);

        assertThat(result).hasSize(1);
        verify(repository).findByCustomerId(customerId);
    }

    // --- refund ---

    @Test
    void refund_setsStatusRefundedAndPublishesEvent() {
        service = newService();
        UUID id = UUID.randomUUID();
        Payment payment = buildPayment(id, PaymentStatus.COMPLETED);
        when(repository.findById(id)).thenReturn(Optional.of(payment));
        when(repository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Payment.class))).thenReturn(mock(PaymentResponse.class));

        service.refund(id);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(eventPublisher).publishPaymentRefunded(any(PaymentRefundedEvent.class));
    }

    @Test
    void refund_notFound_throwsResourceNotFound() {
        service = newService();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refund(id))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(eventPublisher);
    }

    // --- updateStatus ---

    @Test
    void updateStatus_updatesAndReturnsResponse() {
        service = newService();
        UUID id = UUID.randomUUID();
        Payment payment = buildPayment(id, PaymentStatus.PENDING);
        when(repository.findById(id)).thenReturn(Optional.of(payment));
        when(repository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Payment.class))).thenReturn(mock(PaymentResponse.class));

        service.updateStatus(id, PaymentStatus.FAILED);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void updateStatus_notFound_throwsResourceNotFound() {
        service = newService();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(id, PaymentStatus.FAILED))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
