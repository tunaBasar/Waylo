package com.waylo.flightseatinventoryservice;

import com.waylo.flightseatinventoryservice.client.FlightClient;
import com.waylo.flightseatinventoryservice.domain.FlightSeat;
import com.waylo.flightseatinventoryservice.dto.*;
import com.waylo.flightseatinventoryservice.exception.*;
import com.waylo.flightseatinventoryservice.repository.FlightSeatRepository;
import com.waylo.flightseatinventoryservice.service.FlightSeatService;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightSeatServiceTest {

    @Mock FlightSeatRepository repo;
    @Mock FlightClient flightClient;

    FlightSeatService service;

    static final UUID USER = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FlightSeatService(repo, flightClient, 10);
    }

    // ---------- helpers ----------

    private FlightSeat seat(boolean available) {
        FlightSeat s = new FlightSeat();
        s.setFlightNumber("TK100");
        s.setSeatNumber("12A");
        s.setSeatType("ECONOMY");
        s.setPrice(new BigDecimal("150.00"));
        s.setAvailable(available);
        return s;
    }

    private FlightSeat held(UUID by, Instant expires) {
        FlightSeat s = seat(false);
        s.setReservedBy(by);
        s.setReservationId("res-1");
        s.setHoldExpiresAt(expires);
        return s;
    }

    private void flight(String status, Instant departure) {
        when(flightClient.getByFlightNumber(any()))
                .thenReturn(ApiResponse.of(new FlightInfo(status, departure)));
    }

    private void flightOk() {
        flight("SCHEDULED", Instant.now().plus(Duration.ofDays(1)));
    }

    private void findSeat(FlightSeat s) {
        when(repo.findByFlightNumberAndSeatNumber("TK100", "12A")).thenReturn(Optional.of(s));
    }

    private FeignException feign(int status) {
        Request req = Request.create(Request.HttpMethod.GET, "/x", Map.of(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("m", feign.Response.builder().status(status).request(req)
                .headers(Map.of()).build());
    }

    // ---------- reserve ----------

    @Test
    void reserveHappyPathSetsAllFieldsAndExpiryFromConfig() {
        flightOk();
        FlightSeat s = seat(true);
        findSeat(s);
        when(repo.saveAndFlush(s)).thenReturn(s);

        Instant before = Instant.now();
        SeatReservationResponse r = service.reserveSeat("tk100", "12a", USER);

        assertFalse(s.isAvailable());
        assertEquals(USER, s.getReservedBy());
        assertNotNull(s.getReservationId());
        assertEquals(s.getReservationId(), r.reservationId());
        assertFalse(s.getHoldExpiresAt().isBefore(before.plus(Duration.ofMinutes(10))));
        assertFalse(s.getHoldExpiresAt().isAfter(Instant.now().plus(Duration.ofMinutes(10))));
        assertEquals(s.getHoldExpiresAt(), r.holdExpiresAt());
        verify(repo).saveAndFlush(s);
    }

    @Test
    void reserveOtherUsersActiveHoldConflicts() {
        flightOk();
        findSeat(held(OTHER, Instant.now().plusSeconds(300)));
        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.reserveSeat("TK100", "12A", USER));
        assertEquals("SEAT_ALREADY_RESERVED", ex.getCode());
    }

    @Test
    void reserveExpiredHoldSucceeds() {
        flightOk();
        FlightSeat s = held(OTHER, Instant.now().minusSeconds(5));
        findSeat(s);
        when(repo.saveAndFlush(s)).thenReturn(s);
        service.reserveSeat("TK100", "12A", USER);
        assertEquals(USER, s.getReservedBy());
    }

    @Test
    void reserveCancelledFlightRejected() {
        flight("CANCELLED", Instant.now().plus(Duration.ofDays(1)));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.reserveSeat("TK100", "12A", USER));
        assertEquals("FLIGHT_NOT_BOOKABLE", ex.getRuleCode());
    }

    @Test
    void reservePastDepartureRejected() {
        flight("SCHEDULED", Instant.now().minusSeconds(60));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.reserveSeat("TK100", "12A", USER));
        assertEquals("FLIGHT_NOT_BOOKABLE", ex.getRuleCode());
    }

    @Test
    void feignNotFoundMapsToNotFound() {
        when(flightClient.getByFlightNumber(any())).thenThrow(feign(404));
        assertThrows(ResourceNotFoundException.class, () -> service.reserveSeat("TK100", "12A", USER));
    }

    @Test
    void feign5xxMapsToUnavailable() {
        when(flightClient.getByFlightNumber(any())).thenThrow(feign(500));
        assertThrows(ServiceUnavailableException.class, () -> service.reserveSeat("TK100", "12A", USER));
    }

    @Test
    void feignConnectionFailureMapsToUnavailable() {
        Request req = Request.create(Request.HttpMethod.GET, "/x", Map.of(), null, StandardCharsets.UTF_8, null);
        when(flightClient.getByFlightNumber(any()))
                .thenThrow(new RetryableException(-1, "refused", Request.HttpMethod.GET, (Long) null, req));
        assertThrows(ServiceUnavailableException.class, () -> service.reserveSeat("TK100", "12A", USER));
    }

    @Test
    void optimisticLockPropagatesForHandler409() {
        flightOk();
        FlightSeat s = seat(true);
        findSeat(s);
        when(repo.saveAndFlush(s)).thenThrow(new ObjectOptimisticLockingFailureException(FlightSeat.class, "x"));
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> service.reserveSeat("TK100", "12A", USER));
    }

    @Test
    void reserveNormalizesInput() {
        flightOk();
        FlightSeat s = seat(true);
        when(repo.findByFlightNumberAndSeatNumber("TK100", "12A")).thenReturn(Optional.of(s));
        when(repo.saveAndFlush(s)).thenReturn(s);
        service.reserveSeat(" tk100 ", "12a", USER);
        verify(flightClient).getByFlightNumber("TK100");
    }

    @Test
    void reserveInvalidSeatNumberRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.reserveSeat("TK100", "12Z", USER));
        verifyNoInteractions(flightClient);
    }

    // ---------- cancel ----------

    @Test
    void cancelByOwnerClearsFields() {
        FlightSeat s = held(USER, Instant.now().plusSeconds(300));
        findSeat(s);
        service.cancelReservation("TK100", "12A", USER, false);
        assertTrue(s.isAvailable());
        assertNull(s.getReservedBy());
        assertNull(s.getReservationId());
        assertNull(s.getHoldExpiresAt());
    }

    @Test
    void cancelByAdminAllowed() {
        FlightSeat s = held(OTHER, Instant.now().plusSeconds(300));
        findSeat(s);
        service.cancelReservation("TK100", "12A", USER, true);
        assertTrue(s.isAvailable());
    }

    @Test
    void cancelByOtherUserForbidden() {
        findSeat(held(OTHER, Instant.now().plusSeconds(300)));
        assertThrows(ForbiddenException.class, () -> service.cancelReservation("TK100", "12A", USER, false));
    }

    @Test
    void cancelWithoutActiveHoldRejected() {
        findSeat(seat(true));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.cancelReservation("TK100", "12A", USER, false));
        assertEquals("SEAT_NOT_RESERVED", ex.getRuleCode());
    }

    @Test
    void cancelExpiredHoldRejected() {
        findSeat(held(USER, Instant.now().minusSeconds(5)));
        assertThrows(BusinessRuleException.class, () -> service.cancelReservation("TK100", "12A", USER, false));
    }

    // ---------- create / bulk / delete ----------

    @Test
    void createNormalizes() {
        when(repo.existsByFlightNumberAndSeatNumber("AB123", "12A")).thenReturn(false);
        when(repo.save(any(FlightSeat.class))).thenAnswer(i -> i.getArgument(0));
        FlightSeatResponse r = service.createFlightSeat(
                new FlightSeatRequest(" ab123 ", "12a", "economy", new BigDecimal("10.00")));
        assertEquals("AB123", r.flightNumber());
        assertEquals("12A", r.seatNumber());
        assertEquals("ECONOMY", r.seatType());
    }

    @Test
    void createDuplicateConflicts() {
        when(repo.existsByFlightNumberAndSeatNumber("AB123", "12A")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.createFlightSeat(
                new FlightSeatRequest("ab123", "12a", "ECONOMY", BigDecimal.ONE)));
        verify(repo, never()).save(any());
    }

    @Test
    void createInvalidSeatNumberOrTypeRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.createFlightSeat(
                new FlightSeatRequest("AB123", "1234A", "ECONOMY", BigDecimal.ONE)));
        assertThrows(IllegalArgumentException.class, () -> service.createFlightSeat(
                new FlightSeatRequest("AB123", "12L", "ECONOMY", BigDecimal.ONE)));
        assertThrows(IllegalArgumentException.class, () -> service.createFlightSeat(
                new FlightSeatRequest("AB123", "12A", "LUXURY", BigDecimal.ONE)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bulkCreatesCount() {
        when(repo.findByFlightNumber("TK100")).thenReturn(List.of());
        BulkSeatResponse r = service.createSeatsBulk(
                new BulkSeatRequest("tk100", "ECONOMY", BigDecimal.TEN, 1, 30, "ABCDEF"));
        assertEquals(180, r.created());
        ArgumentCaptor<List<FlightSeat>> cap = ArgumentCaptor.forClass(List.class);
        verify(repo).saveAll(cap.capture());
        assertEquals(180, cap.getValue().size());
    }

    @Test
    void bulkOver600Rejected() {
        assertThrows(IllegalArgumentException.class, () -> service.createSeatsBulk(
                new BulkSeatRequest("TK100", "ECONOMY", BigDecimal.TEN, 1, 101, "ABCDEF")));
        verify(repo, never()).saveAll(any());
    }

    @Test
    void bulkExactly600Allowed() {
        when(repo.findByFlightNumber("TK100")).thenReturn(List.of());
        assertEquals(600, service.createSeatsBulk(
                new BulkSeatRequest("TK100", "ECONOMY", BigDecimal.TEN, 1, 100, "ABCDEF")).created());
    }

    @Test
    void bulkDuplicateSavesNothing() {
        FlightSeat existing = seat(true);
        existing.setSeatNumber("2B");
        when(repo.findByFlightNumber("TK100")).thenReturn(List.of(existing));
        assertThrows(DuplicateResourceException.class, () -> service.createSeatsBulk(
                new BulkSeatRequest("TK100", "ECONOMY", BigDecimal.TEN, 1, 3, "ABC")));
        verify(repo, never()).saveAll(any());
    }

    @Test
    void bulkInvalidLettersOrRowsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.createSeatsBulk(
                new BulkSeatRequest("TK100", "ECONOMY", BigDecimal.TEN, 1, 3, "AAB")));
        assertThrows(IllegalArgumentException.class, () -> service.createSeatsBulk(
                new BulkSeatRequest("TK100", "ECONOMY", BigDecimal.TEN, 1, 3, "AL")));
        assertThrows(IllegalArgumentException.class, () -> service.createSeatsBulk(
                new BulkSeatRequest("TK100", "ECONOMY", BigDecimal.TEN, 5, 3, "A")));
    }

    @Test
    void deleteHeldSeatRejected() {
        findSeat(held(USER, Instant.now().plusSeconds(300)));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.deleteSeat("TK100", "12A"));
        assertEquals("SEAT_HELD", ex.getRuleCode());
        verify(repo, never()).delete(any());
    }

    @Test
    void deleteFreeAndExpiredSeatsAllowed() {
        FlightSeat free = seat(true);
        findSeat(free);
        service.deleteSeat("tk100", "12a");
        verify(repo).delete(free);

        FlightSeat expired = held(USER, Instant.now().minusSeconds(5));
        findSeat(expired);
        service.deleteSeat("TK100", "12A");
        verify(repo).delete(expired);
    }

    // ---------- reads ----------

    @Test
    void publicResponseHasNoReservationFieldsAndComputesAvailability() {
        findSeat(held(OTHER, Instant.now().minusSeconds(5)));
        FlightSeatResponse r = service.getFlightSeat("tk100", "12a");
        assertTrue(r.available());
        Set<String> names = new HashSet<>();
        for (var c : FlightSeatResponse.class.getRecordComponents()) names.add(c.getName());
        assertFalse(names.contains("reservationId"));
        assertFalse(names.contains("reservedBy"));

        findSeat(held(OTHER, Instant.now().plusSeconds(60)));
        assertFalse(service.getFlightSeat("TK100", "12A").available());
    }

    @Test
    void availableAndCountUseNowParameter() {
        when(repo.countAvailable(eq("TK100"), any(Instant.class))).thenReturn(7L);
        assertEquals(7L, service.getAvailableSeatCount(" tk100"));
        service.getAvailableSeats("tk100");
        verify(repo).findAvailable(eq("TK100"), any(Instant.class));
    }
}
