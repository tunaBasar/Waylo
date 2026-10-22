package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.dto.DailyRateResponse;
import com.waylo.hotelservice.dto.HotelCurrencyResponse;
import com.waylo.hotelservice.dto.RateUpdateItem;
import com.waylo.hotelservice.dto.RateUpdateRequest;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.security.HotelAccessGuard;
import com.waylo.hotelservice.service.RateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateControllerTest {

    private final RateService rateService = mock(RateService.class);
    private final HotelAccessGuard accessGuard = mock(HotelAccessGuard.class);
    @InjectMocks
    private RateController controller;

    private static final UUID HOTEL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ROOM_TYPE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID RATE_PLAN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private DailyRateResponse buildResponse() {
        return new DailyRateResponse(
                UUID.randomUUID(), HOTEL_ID, ROOM_TYPE_ID, RATE_PLAN_ID,
                LocalDate.of(2026, 10, 20), 2, new BigDecimal("120.50"), "USD", 1L,
                Instant.now()
        );
    }

    private RateUpdateRequest buildRequest() {
        return new RateUpdateRequest(List.of(
                new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                        LocalDate.of(2026, 10, 20), 2, new BigDecimal("100.00"), "USD", null)));
    }

    // --- GET RATES ---

    @Test
    void getRates_guardThrows_accessDenied() {
        doThrow(new BusinessRuleException("ACCESS_DENIED", "Not allowed"))
                .when(accessGuard).requireRoomManage(USER_ID, HOTEL_ID);

        assertThatThrownBy(() -> controller.getRates(USER_ID, HOTEL_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), null, null))
                .isInstanceOf(BusinessRuleException.class);

        verify(rateService, never()).getRates(any(), any(), any(), any(), any());
    }

    @Test
    void getRates_noUserId_guardThrows() {
        doThrow(new BusinessRuleException("ACCESS_DENIED", "Not allowed"))
                .when(accessGuard).requireRoomManage(null, HOTEL_ID);

        assertThatThrownBy(() -> controller.getRates(null, HOTEL_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), null, null))
                .isInstanceOf(BusinessRuleException.class);

        verify(rateService, never()).getRates(any(), any(), any(), any(), any());
    }

    @Test
    void getRates_guardReturns_serviceCalled() {
        doNothing().when(accessGuard).requireRoomManage(USER_ID, HOTEL_ID);
        when(rateService.getRates(eq(HOTEL_ID), any(LocalDate.class), any(LocalDate.class), any(), any()))
                .thenReturn(List.of());

        ResponseEntity<?> result = controller.getRates(USER_ID, HOTEL_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), null, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(rateService).getRates(eq(HOTEL_ID), any(LocalDate.class), any(LocalDate.class), any(), any());
    }

    @Test
    void getRates_serviceReturnsData_200() {
        doNothing().when(accessGuard).requireRoomManage(USER_ID, HOTEL_ID);
        when(rateService.getRates(eq(HOTEL_ID), any(), any(), any(), any()))
                .thenReturn(List.of(buildResponse()));

        ResponseEntity<?> result = controller.getRates(USER_ID, HOTEL_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), null, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
    }

    @Test
    void getRates_withRoomTypeIdOption_passesToService() {
        doNothing().when(accessGuard).requireRoomManage(USER_ID, HOTEL_ID);
        when(rateService.getRates(eq(HOTEL_ID), any(), any(), eq(ROOM_TYPE_ID), any()))
                .thenReturn(List.of());

        controller.getRates(USER_ID, HOTEL_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), ROOM_TYPE_ID, null);

        verify(rateService).getRates(eq(HOTEL_ID), any(), any(), eq(ROOM_TYPE_ID), any());
    }

    @Test
    void getRates_withRatePlanIdOption_passesToService() {
        doNothing().when(accessGuard).requireRoomManage(USER_ID, HOTEL_ID);
        when(rateService.getRates(eq(HOTEL_ID), any(), any(), any(), eq(RATE_PLAN_ID)))
                .thenReturn(List.of());

        controller.getRates(USER_ID, HOTEL_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), null, RATE_PLAN_ID);

        verify(rateService).getRates(eq(HOTEL_ID), any(), any(), any(), eq(RATE_PLAN_ID));
    }

    // --- UPDATE RATES ---

    @Test
    void updateRates_guardThrows_accessDenied() {
        doThrow(new BusinessRuleException("ACCESS_DENIED", "Not allowed"))
                .when(accessGuard).requireRoomManage(USER_ID, HOTEL_ID);

        assertThatThrownBy(() -> controller.updateRates(USER_ID, HOTEL_ID, buildRequest()))
                .isInstanceOf(BusinessRuleException.class);

        verify(rateService, never()).updateRates(any(), any());
    }

    @Test
    void updateRates_noUserId_guardThrows() {
        doThrow(new BusinessRuleException("ACCESS_DENIED", "Not allowed"))
                .when(accessGuard).requireRoomManage(null, HOTEL_ID);

        assertThatThrownBy(() -> controller.updateRates(null, HOTEL_ID, buildRequest()))
                .isInstanceOf(BusinessRuleException.class);

        verify(rateService, never()).updateRates(any(), any());
    }

    @Test
    void updateRates_guardReturns_serviceCalled() {
        doNothing().when(accessGuard).requireRoomManage(USER_ID, HOTEL_ID);
        when(rateService.updateRates(eq(HOTEL_ID), any()))
                .thenReturn(List.of(buildResponse()));

        ResponseEntity<?> result = controller.updateRates(USER_ID, HOTEL_ID, buildRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        verify(rateService).updateRates(eq(HOTEL_ID), any());
    }

    @Test
    void updateRates_emptyItems_delegatesToService() {
        doNothing().when(accessGuard).requireRoomManage(USER_ID, HOTEL_ID);
        when(rateService.updateRates(eq(HOTEL_ID), any()))
                .thenReturn(List.of(buildResponse()));
        RateUpdateRequest body = new RateUpdateRequest(List.of());

        ResponseEntity<?> result = controller.updateRates(USER_ID, HOTEL_ID, body);

        // Controller delegates to service; no empty-items check in controller
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(rateService).updateRates(eq(HOTEL_ID), any());
    }

    @Test
    void updateRates_serviceThrowsBusinessRule_bubblesException() {
        doNothing().when(accessGuard).requireRoomManage(USER_ID, HOTEL_ID);
        when(rateService.updateRates(any(), any()))
                .thenThrow(new BusinessRuleException("OCCUPANCY_RULE", "Occupancy too high"));

        assertThatThrownBy(() -> controller.updateRates(USER_ID, HOTEL_ID, buildRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Occupancy");
    }

    @Test
    void updateRates_serviceThrowsResourceNotFound_bubblesException() {
        doNothing().when(accessGuard).requireRoomManage(USER_ID, HOTEL_ID);
        when(rateService.updateRates(any(), any()))
                .thenThrow(new ResourceNotFoundException("Room type", "id", ROOM_TYPE_ID));

        assertThatThrownBy(() -> controller.updateRates(USER_ID, HOTEL_ID, buildRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Room type");
    }

    // --- GET CURRENCY ---

    @Test
    void getCurrency_guardThrows_accessDenied() {
        doThrow(new BusinessRuleException("ACCESS_DENIED", "Not allowed"))
                .when(accessGuard).requireRoomManage(USER_ID, HOTEL_ID);

        assertThatThrownBy(() -> controller.getCurrency(USER_ID, HOTEL_ID))
                .isInstanceOf(BusinessRuleException.class);

        verify(rateService, never()).getHotelCurrency(any());
    }

    @Test
    void getCurrency_noUserId_guardThrows() {
        doThrow(new BusinessRuleException("ACCESS_DENIED", "Not allowed"))
                .when(accessGuard).requireRoomManage(null, HOTEL_ID);

        assertThatThrownBy(() -> controller.getCurrency(null, HOTEL_ID))
                .isInstanceOf(BusinessRuleException.class);

        verify(rateService, never()).getHotelCurrency(any());
    }

    @Test
    void getCurrency_authorized_returns200WithCurrency() {
        doNothing().when(accessGuard).requireRoomManage(USER_ID, HOTEL_ID);
        when(rateService.getHotelCurrency(HOTEL_ID))
                .thenReturn(new HotelCurrencyResponse("EUR"));

        ResponseEntity<?> result = controller.getCurrency(USER_ID, HOTEL_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        var body = (com.waylo.hotelservice.dto.ApiResponse<HotelCurrencyResponse>) result.getBody();
        assertThat(body.data()).isNotNull();
        assertThat(body.data().currency()).isEqualTo("EUR");
        verify(accessGuard).requireRoomManage(USER_ID, HOTEL_ID);
    }

    @Test
    void getCurrency_noRates_returns200WithDataNull() {
        doNothing().when(accessGuard).requireRoomManage(USER_ID, HOTEL_ID);
        when(rateService.getHotelCurrency(HOTEL_ID))
                .thenReturn(null);

        ResponseEntity<?> result = controller.getCurrency(USER_ID, HOTEL_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        var body = (com.waylo.hotelservice.dto.ApiResponse<HotelCurrencyResponse>) result.getBody();
        assertThat(body.data()).isNull();
    }
}
