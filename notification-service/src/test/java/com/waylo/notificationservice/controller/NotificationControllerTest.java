package com.waylo.notificationservice.controller;

import com.waylo.notificationservice.domain.NotificationStatus;
import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.dto.ApiResponse;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.dto.NotificationResponse;
import com.waylo.notificationservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;
    @InjectMocks
    private NotificationController controller;

    private final UUID notificationId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    private NotificationResponse buildResponse() {
        return new NotificationResponse(notificationId, customerId, bookingId, NotificationType.EMAIL,
                "test@waylo.com", "Subject", "Body", NotificationStatus.SENT, 0, Instant.now(), Instant.now(), Instant.now());
    }

    @Test
    void send_returns201() {
        NotificationRequest request = new NotificationRequest(customerId, bookingId, NotificationType.EMAIL,
                "test@waylo.com", "Subject", "Body");
        NotificationResponse response = buildResponse();
        when(notificationService.send(any(NotificationRequest.class))).thenReturn(response);

        ResponseEntity<ApiResponse<NotificationResponse>> result = controller.send(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getById_returns200() {
        NotificationResponse response = buildResponse();
        when(notificationService.getById(notificationId)).thenReturn(response);

        ResponseEntity<ApiResponse<NotificationResponse>> result = controller.getById(notificationId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getAll_returns200() {
        when(notificationService.getAll()).thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<NotificationResponse>>> result = controller.getAll();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).hasSize(1);
    }

    @Test
    void getByCustomerId_returns200() {
        when(notificationService.getByCustomerId(customerId)).thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<NotificationResponse>>> result = controller.getByCustomerId(customerId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).getByCustomerId(customerId);
    }

    @Test
    void getByBookingId_returns200() {
        when(notificationService.getByBookingId(bookingId)).thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<NotificationResponse>>> result = controller.getByBookingId(bookingId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).getByBookingId(bookingId);
    }

    @Test
    void retry_returns200() {
        NotificationResponse retried = new NotificationResponse(notificationId, customerId, bookingId, NotificationType.EMAIL,
                "test@waylo.com", "Subject", "Body", NotificationStatus.SENT, 1, Instant.now(), Instant.now(), Instant.now());
        when(notificationService.retry(notificationId)).thenReturn(retried);

        ResponseEntity<ApiResponse<NotificationResponse>> result = controller.retry(notificationId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data().retryCount()).isEqualTo(1);
    }
}
