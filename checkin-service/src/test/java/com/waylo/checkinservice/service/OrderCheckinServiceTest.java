package com.waylo.checkinservice.service;

import com.waylo.checkinservice.client.OrderItemClient;
import com.waylo.checkinservice.client.OrderItemClient.OrderItemDto;
import com.waylo.checkinservice.domain.Checkin;
import com.waylo.checkinservice.domain.CheckinStatus;
import com.waylo.checkinservice.dto.ApiResponse;
import com.waylo.checkinservice.dto.OrderCheckinResponse;
import com.waylo.checkinservice.exception.CheckinException;
import com.waylo.checkinservice.repository.CheckinRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCheckinServiceTest {

    @Mock CheckinRepository repository;
    @Mock OrderItemClient client;

    private final Instant departure = Instant.parse("2026-11-10T12:00:00Z");
    private final UUID itemId = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    private OrderCheckinService at(Instant now) {
        return new OrderCheckinService(repository, client, Clock.fixed(now, ZoneOffset.UTC));
    }

    private OrderItemDto item(String orderStatus, String cancelOutcome, String supplier, String voucher) {
        return new OrderItemDto(itemId, UUID.randomUUID(), owner, orderStatus, "FLIGHT", cancelOutcome, supplier, "TK100",
                departure, "3A", "ABC234", "Ayse", "Demir", voucher);
    }

    private void stub(OrderItemDto dto) {
        when(client.get(itemId)).thenReturn(new ApiResponse<>(true, dto, null, null));
    }

    private void stubSave() {
        when(repository.findByOrderItemId(itemId)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Checkin.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void assertRejected(OrderCheckinService s, UUID user, int status, String code) {
        assertThatThrownBy(() -> s.checkIn(itemId, user)).isInstanceOfSatisfying(CheckinException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    @Test
    void opensExactly24HoursBefore() {
        stub(item("CONFIRMED", null, "INTERNAL", "ACTIVE"));
        stubSave();
        OrderCheckinResponse r = at(departure.minus(Duration.ofHours(24))).checkIn(itemId, owner);
        assertThat(r.status()).isEqualTo(CheckinStatus.CHECKED_IN);
        assertThat(r.seatNumber()).isEqualTo("3A");
        assertThat(r.pnr()).isEqualTo("ABC234");
        assertThat(r.passengerName()).isEqualTo("Ayse Demir");
        assertThat(r.checkedInAt()).isNotNull();
    }

    @Test
    void notOpenOneSecondBeforeWindow() {
        stub(item("CONFIRMED", null, "INTERNAL", "ACTIVE"));
        when(repository.findByOrderItemId(itemId)).thenReturn(Optional.empty());
        assertRejected(at(departure.minus(Duration.ofHours(24)).minusSeconds(1)), owner, 422, "CHECKIN_NOT_OPEN");
    }

    @Test
    void closesAfterOneHourBefore() {
        stub(item("CONFIRMED", null, "INTERNAL", "ACTIVE"));
        when(repository.findByOrderItemId(itemId)).thenReturn(Optional.empty());
        assertRejected(at(departure.minus(Duration.ofHours(1)).plusSeconds(1)), owner, 422, "CHECKIN_CLOSED");
    }

    @Test
    void stillOpenExactlyOneHourBefore() {
        stub(item("CONFIRMED", null, "INTERNAL", "ACTIVE"));
        stubSave();
        assertThat(at(departure.minus(Duration.ofHours(1))).checkIn(itemId, owner).status()).isEqualTo(CheckinStatus.CHECKED_IN);
    }

    @Test
    void otherUserForbidden() {
        stub(item("CONFIRMED", null, "INTERNAL", "ACTIVE"));
        assertRejected(at(departure.minus(Duration.ofHours(5))), UUID.randomUUID(), 403, "FORBIDDEN");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void cancelledOrderOrRefundedItemConflicts() {
        stub(item("CANCELLED", null, "INTERNAL", "VOID"));
        assertRejected(at(departure.minus(Duration.ofHours(5))), owner, 409, "ORDER_ITEM_CANCELLED");
        stub(item("CONFIRMED", "REFUND", "INTERNAL", "ACTIVE"));
        assertRejected(at(departure.minus(Duration.ofHours(5))), owner, 409, "ORDER_ITEM_CANCELLED");
    }

    @Test
    void redeemedItemCannotCheckIn() {
        stub(item("CONFIRMED", null, "INTERNAL", "REDEEMED"));
        when(repository.findByOrderItemId(itemId)).thenReturn(Optional.empty());
        assertRejected(at(departure.minus(Duration.ofHours(5))), owner, 409, "ALREADY_BOARDED");
    }

    @Test
    void redeemedItemKeepsExistingCheckin() {
        stub(item("CONFIRMED", null, "INTERNAL", "REDEEMED"));
        Checkin existing = new Checkin();
        existing.setOrderItemId(itemId);
        existing.setStatus(CheckinStatus.CHECKED_IN);
        when(repository.findByOrderItemId(itemId)).thenReturn(Optional.of(existing));
        assertThat(at(departure.minus(Duration.ofMinutes(30))).checkIn(itemId, owner).status()).isEqualTo(CheckinStatus.CHECKED_IN);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void repeatIsIdempotent() {
        stub(item("CONFIRMED", null, "INTERNAL", "ACTIVE"));
        Checkin existing = new Checkin();
        existing.setOrderItemId(itemId);
        existing.setSeatNumber("3A");
        existing.setStatus(CheckinStatus.CHECKED_IN);
        existing.setCheckedInAt(Instant.parse("2026-11-09T13:00:00Z"));
        when(repository.findByOrderItemId(itemId)).thenReturn(Optional.of(existing));
        OrderCheckinResponse r = at(departure.minus(Duration.ofHours(3))).checkIn(itemId, owner);
        assertThat(r.checkedInAt()).isEqualTo(Instant.parse("2026-11-09T13:00:00Z"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void externalSupplierIsCheckedInAtAirline() {
        stub(item("CONFIRMED", null, "DUFFEL", "ACTIVE"));
        assertRejected(at(departure.minus(Duration.ofHours(5))), owner, 409, "CHECKIN_AT_AIRLINE");
    }

    @Test
    void adminListRequiresAdmin() {
        assertThatThrownBy(() -> at(departure).adminList("CUSTOMER", 0, 20)).isInstanceOf(CheckinException.class);
    }
}
