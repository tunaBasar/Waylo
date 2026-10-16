package com.waylo.checkinservice.controller;

import com.waylo.checkinservice.domain.CheckinStatus;
import com.waylo.checkinservice.dto.ApiResponse;
import com.waylo.checkinservice.dto.CheckinRequest;
import com.waylo.checkinservice.dto.CheckinResponse;
import com.waylo.checkinservice.service.CheckinService;
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
class CheckinControllerTest {

    @Mock
    private CheckinService checkinService;
    @InjectMocks
    private CheckinController controller;

    private final UUID checkinId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID flightId = UUID.randomUUID();

    private CheckinResponse buildResponse(CheckinStatus status) {
        return new CheckinResponse(checkinId, bookingId, customerId, flightId,
                "12A", null, status, null, Instant.now(), Instant.now());
    }

    @Test
    void checkIn_returns201() {
        CheckinRequest request = new CheckinRequest(bookingId, customerId, flightId, "12A");
        CheckinResponse response = buildResponse(CheckinStatus.CHECKED_IN);
        when(checkinService.checkIn(any(CheckinRequest.class))).thenReturn(response);

        ResponseEntity<ApiResponse<CheckinResponse>> result = controller.checkIn(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getById_returns200() {
        CheckinResponse response = buildResponse(CheckinStatus.CHECKED_IN);
        when(checkinService.getById(checkinId)).thenReturn(response);

        ResponseEntity<ApiResponse<CheckinResponse>> result = controller.getById(checkinId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getAll_returns200() {
        when(checkinService.getAll()).thenReturn(List.of(buildResponse(CheckinStatus.CHECKED_IN)));

        ResponseEntity<ApiResponse<List<CheckinResponse>>> result = controller.getAll();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).hasSize(1);
    }

    @Test
    void getByBookingId_returns200() {
        when(checkinService.getByBookingId(bookingId)).thenReturn(List.of(buildResponse(CheckinStatus.CHECKED_IN)));

        ResponseEntity<ApiResponse<List<CheckinResponse>>> result = controller.getByBookingId(bookingId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(checkinService).getByBookingId(bookingId);
    }

    @Test
    void getByFlightId_returns200() {
        when(checkinService.getByFlightId(flightId)).thenReturn(List.of(buildResponse(CheckinStatus.CHECKED_IN)));

        ResponseEntity<ApiResponse<List<CheckinResponse>>> result = controller.getByFlightId(flightId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(checkinService).getByFlightId(flightId);
    }

    @Test
    void issueBoardingPass_returns200() {
        CheckinResponse response = buildResponse(CheckinStatus.BOARDING_PASS_ISSUED);
        when(checkinService.issueBoardingPass(checkinId)).thenReturn(response);

        ResponseEntity<ApiResponse<CheckinResponse>> result = controller.issueBoardingPass(checkinId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data().status()).isEqualTo(CheckinStatus.BOARDING_PASS_ISSUED);
    }

    @Test
    void cancel_returns200() {
        CheckinResponse response = buildResponse(CheckinStatus.CANCELLED);
        when(checkinService.cancel(checkinId)).thenReturn(response);

        ResponseEntity<ApiResponse<CheckinResponse>> result = controller.cancel(checkinId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data().status()).isEqualTo(CheckinStatus.CANCELLED);
    }
}
