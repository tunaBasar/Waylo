package com.waylo.notificationservice.service;

import com.waylo.notificationservice.domain.Notification;
import com.waylo.notificationservice.domain.NotificationStatus;
import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.dto.NotificationMapper;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.dto.NotificationResponse;
import com.waylo.notificationservice.exception.ResourceNotFoundException;
import com.waylo.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository repository;
    @Mock
    private NotificationMapper mapper;
    @Mock
    private EmailService emailService;

    private NotificationServiceImpl service;

    private final UUID notificationId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    private NotificationServiceImpl newService() {
        return new NotificationServiceImpl(repository, mapper, emailService);
    }

    private Notification buildNotification(UUID id, NotificationStatus status) {
        Notification n = new Notification();
        n.setId(id);
        n.setCustomerId(customerId);
        n.setBookingId(bookingId);
        n.setType(NotificationType.EMAIL);
        n.setRecipient("test@waylo.com");
        n.setSubject("Subject");
        n.setBody("Body");
        n.setStatus(status);
        return n;
    }

    // --- send ---

    @Test
    void send_setsStatusSentAndSentAt() {
        service = newService();
        NotificationRequest request = new NotificationRequest(customerId, bookingId, NotificationType.EMAIL,
                "test@waylo.com", "Subject", "Body");
        Notification mapped = new Notification();
        mapped.setCustomerId(customerId);
        mapped.setBookingId(bookingId);
        mapped.setType(NotificationType.EMAIL);
        mapped.setRecipient("test@waylo.com");
        mapped.setSubject("Subject");
        mapped.setBody("Body");

        when(mapper.toEntity(request)).thenReturn(mapped);
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        NotificationResponse response = mock(NotificationResponse.class);
        when(mapper.toResponse(any(Notification.class))).thenReturn(response);

        when(emailService.deliver(eq("test@waylo.com"), eq("Subject"), anyString())).thenReturn(EmailService.Delivery.SENT);

        NotificationResponse result = service.send(request);

        assertThat(result).isEqualTo(response);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getSentAt()).isNotNull();
    }

    // --- getById ---

    @Test
    void getById_found_returnsResponse() {
        service = newService();
        Notification notification = buildNotification(notificationId, NotificationStatus.SENT);
        NotificationResponse response = mock(NotificationResponse.class);

        when(repository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(mapper.toResponse(notification)).thenReturn(response);

        NotificationResponse result = service.getById(notificationId);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getById_notFound_throwsException() {
        service = newService();
        when(repository.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(notificationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getAll ---

    @Test
    void getAll_returnsMappedList() {
        service = newService();
        Notification notification = buildNotification(notificationId, NotificationStatus.SENT);
        NotificationResponse response = mock(NotificationResponse.class);

        when(repository.findAll()).thenReturn(List.of(notification));
        when(mapper.toResponse(notification)).thenReturn(response);

        List<NotificationResponse> result = service.getAll();

        assertThat(result).containsExactly(response);
    }

    // --- getByCustomerId ---

    @Test
    void getByCustomerId_returnsMappedList() {
        service = newService();
        Notification notification = buildNotification(notificationId, NotificationStatus.SENT);
        NotificationResponse response = mock(NotificationResponse.class);

        when(repository.findByCustomerId(customerId)).thenReturn(List.of(notification));
        when(mapper.toResponse(notification)).thenReturn(response);

        List<NotificationResponse> result = service.getByCustomerId(customerId);

        assertThat(result).containsExactly(response);
    }

    // --- getByBookingId ---

    @Test
    void getByBookingId_returnsMappedList() {
        service = newService();
        Notification notification = buildNotification(notificationId, NotificationStatus.SENT);
        NotificationResponse response = mock(NotificationResponse.class);

        when(repository.findByBookingId(bookingId)).thenReturn(List.of(notification));
        when(mapper.toResponse(notification)).thenReturn(response);

        List<NotificationResponse> result = service.getByBookingId(bookingId);

        assertThat(result).containsExactly(response);
    }

    // --- retry ---

    @Test
    void retry_incrementsRetryCountAndMarksSent() {
        service = newService();
        Notification notification = buildNotification(notificationId, NotificationStatus.FAILED);
        notification.setRetryCount(2);
        NotificationResponse response = mock(NotificationResponse.class);

        when(repository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Notification.class))).thenReturn(response);

        when(emailService.deliver(eq("test@waylo.com"), eq("Subject"), anyString())).thenReturn(EmailService.Delivery.SENT);

        NotificationResponse result = service.retry(notificationId);

        assertThat(result).isEqualTo(response);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved).isSameAs(notification);
        assertThat(saved.getRetryCount()).isEqualTo(3);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getSentAt()).isNotNull();
    }

    @Test
    void retry_notFound_throwsException() {
        service = newService();
        when(repository.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retry(notificationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
    // --- real delivery outcome ---

    private Notification mapped(NotificationType type, String recipient) {
        Notification n = new Notification();
        n.setCustomerId(customerId);
        n.setType(type);
        n.setRecipient(recipient);
        n.setSubject("Subject");
        n.setBody("Line <1>\nLine 2");
        return n;
    }

    private Notification sendAndCapture(NotificationType type, String recipient) {
        service = newService();
        NotificationRequest request = new NotificationRequest(customerId, bookingId, type, recipient, "Subject", "Body");
        when(mapper.toEntity(request)).thenReturn(mapped(type, recipient));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        service.send(request);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void send_suppressedDomain_isRecordedAsSuppressedNotSent() {
        when(emailService.deliver(anyString(), anyString(), anyString())).thenReturn(EmailService.Delivery.SUPPRESSED);
        Notification saved = sendAndCapture(NotificationType.EMAIL, "user@example.test");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SUPPRESSED);
        assertThat(saved.getSentAt()).isNull();
    }

    @Test
    void send_smtpFailure_isRecordedAsFailed() {
        when(emailService.deliver(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("smtp down"));
        Notification saved = sendAndCapture(NotificationType.EMAIL, "user@waylo.com");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(saved.getSentAt()).isNull();
    }

    @Test
    void send_smsWithoutProvider_staysPending() {
        Notification saved = sendAndCapture(NotificationType.SMS, "+900000000000");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        org.mockito.Mockito.verifyNoInteractions(emailService);
    }

    @Test
    void send_htmlEscapesBody() {
        when(emailService.deliver(anyString(), anyString(), anyString())).thenReturn(EmailService.Delivery.SENT);
        sendAndCapture(NotificationType.EMAIL, "user@waylo.com");
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).deliver(eq("user@waylo.com"), eq("Subject"), html.capture());
        assertThat(html.getValue()).contains("Line &lt;1&gt;<br>Line 2").doesNotContain("<1>");
    }
}
