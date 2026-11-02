package com.waylo.notificationservice.controller;

import com.waylo.notificationservice.domain.NotificationStatus;
import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.dto.NotificationRequest;
import com.waylo.notificationservice.dto.NotificationResponse;
import com.waylo.notificationservice.exception.AccessDeniedException;
import com.waylo.notificationservice.exception.AuthenticationRequiredException;
import com.waylo.notificationservice.exception.ResourceNotFoundException;
import com.waylo.notificationservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private final UUID other = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    private NotificationResponse resp(UUID owner) {
        return new NotificationResponse(notificationId, owner, bookingId, NotificationType.EMAIL,
                "test@example.test", "Subject", "Body", NotificationStatus.SENT, 0, Instant.now(), Instant.now(), Instant.now());
    }

    private NotificationRequest req() {
        return new NotificationRequest(customerId, bookingId, NotificationType.EMAIL, "t@example.test", "S", "B");
    }

    // send
    @Test void send_admin_201() {
        when(notificationService.send(any())).thenReturn(resp(customerId));
        assertThat(controller.send(admin.toString(), "ADMIN", req()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
    @Test void send_customer_403() {
        assertThatThrownBy(() -> controller.send(customerId.toString(), "CUSTOMER", req())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(notificationService);
    }
    @Test void send_anonymous_401() {
        assertThatThrownBy(() -> controller.send(null, null, req())).isInstanceOf(AuthenticationRequiredException.class);
    }

    // getAll
    @Test void getAll_admin_ok() {
        when(notificationService.getAll()).thenReturn(List.of(resp(customerId)));
        assertThat(controller.getAll(admin.toString(), "ADMIN").getBody().data()).hasSize(1);
    }
    @Test void getAll_customer_403() {
        assertThatThrownBy(() -> controller.getAll(customerId.toString(), "CUSTOMER")).isInstanceOf(AccessDeniedException.class);
    }
    @Test void getAll_anonymous_401() {
        assertThatThrownBy(() -> controller.getAll(null, null)).isInstanceOf(AuthenticationRequiredException.class);
    }
    @Test void getAll_malformedUserId_401() {
        assertThatThrownBy(() -> controller.getAll("not-a-uuid", "ADMIN")).isInstanceOf(AuthenticationRequiredException.class);
    }

    // me
    @Test void me_returnsOwnOnly() {
        when(notificationService.getByCustomerId(customerId)).thenReturn(List.of(resp(customerId)));
        assertThat(controller.mine(customerId.toString(), "CUSTOMER").getBody().data()).hasSize(1);
        verify(notificationService).getByCustomerId(customerId);
    }
    @Test void me_anonymous_401() {
        assertThatThrownBy(() -> controller.mine(null, null)).isInstanceOf(AuthenticationRequiredException.class);
    }

    // getById
    @Test void getById_owner_ok() {
        when(notificationService.getById(notificationId)).thenReturn(resp(customerId));
        assertThat(controller.getById(customerId.toString(), "CUSTOMER", notificationId).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void getById_foreignCustomer_404() {
        when(notificationService.getById(notificationId)).thenReturn(resp(customerId));
        assertThatThrownBy(() -> controller.getById(other.toString(), "CUSTOMER", notificationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void getById_admin_ok() {
        when(notificationService.getById(notificationId)).thenReturn(resp(customerId));
        assertThat(controller.getById(admin.toString(), "ADMIN", notificationId).getBody().data().customerId()).isEqualTo(customerId);
    }
    @Test void getById_anonymous_401() {
        assertThatThrownBy(() -> controller.getById(null, null, notificationId)).isInstanceOf(AuthenticationRequiredException.class);
    }

    // by customer
    @Test void byCustomer_self_ok() {
        when(notificationService.getByCustomerId(customerId)).thenReturn(List.of(resp(customerId)));
        assertThat(controller.getByCustomerId(customerId.toString(), "CUSTOMER", customerId).getBody().data()).hasSize(1);
    }
    @Test void byCustomer_other_403() {
        assertThatThrownBy(() -> controller.getByCustomerId(other.toString(), "CUSTOMER", customerId))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(notificationService);
    }
    @Test void byCustomer_admin_ok() {
        when(notificationService.getByCustomerId(customerId)).thenReturn(List.of(resp(customerId)));
        assertThat(controller.getByCustomerId(admin.toString(), "ADMIN", customerId).getBody().data()).hasSize(1);
    }
    @Test void byCustomer_anonymous_401() {
        assertThatThrownBy(() -> controller.getByCustomerId(null, null, customerId)).isInstanceOf(AuthenticationRequiredException.class);
    }

    // by booking
    @Test void byBooking_customerSeesOnlyOwn() {
        when(notificationService.getByBookingId(bookingId)).thenReturn(List.of(resp(customerId), resp(other)));
        List<NotificationResponse> r = controller.getByBookingId(customerId.toString(), "CUSTOMER", bookingId).getBody().data();
        assertThat(r).hasSize(1);
        assertThat(r.get(0).customerId()).isEqualTo(customerId);
    }
    @Test void byBooking_adminSeesAll() {
        when(notificationService.getByBookingId(bookingId)).thenReturn(List.of(resp(customerId), resp(other)));
        assertThat(controller.getByBookingId(admin.toString(), "ADMIN", bookingId).getBody().data()).hasSize(2);
    }
    @Test void byBooking_anonymous_401() {
        assertThatThrownBy(() -> controller.getByBookingId(null, null, bookingId)).isInstanceOf(AuthenticationRequiredException.class);
    }

    // retry
    @Test void retry_admin_ok() {
        when(notificationService.retry(notificationId)).thenReturn(resp(customerId));
        assertThat(controller.retry(admin.toString(), "ADMIN", notificationId).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void retry_customerEvenOwner_403() {
        assertThatThrownBy(() -> controller.retry(customerId.toString(), "CUSTOMER", notificationId)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(notificationService);
    }
    @Test void retry_anonymous_401() {
        assertThatThrownBy(() -> controller.retry(null, null, notificationId)).isInstanceOf(AuthenticationRequiredException.class);
    }
}
