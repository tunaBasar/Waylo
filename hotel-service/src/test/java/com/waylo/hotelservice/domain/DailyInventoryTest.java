package com.waylo.hotelservice.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DailyInventoryTest {

    private final UUID id = UUID.randomUUID();
    private final UUID hotelId = UUID.randomUUID();
    private final UUID roomTypeId = UUID.randomUUID();

    @Test
    void getAndSetId() {
        DailyInventory entity = new DailyInventory();
        entity.setId(id);
        assertThat(entity.getId()).isEqualTo(id);
    }

    @Test
    void getAndSetHotelId() {
        DailyInventory entity = new DailyInventory();
        entity.setHotelId(hotelId);
        assertThat(entity.getHotelId()).isEqualTo(hotelId);
    }

    @Test
    void getAndSetRoomTypeId() {
        DailyInventory entity = new DailyInventory();
        entity.setRoomTypeId(roomTypeId);
        assertThat(entity.getRoomTypeId()).isEqualTo(roomTypeId);
    }

    @Test
    void getAndSetStayDate() {
        DailyInventory entity = new DailyInventory();
        LocalDate date = LocalDate.of(2026, 12, 25);
        entity.setStayDate(date);
        assertThat(entity.getStayDate()).isEqualTo(date);
    }

    @Test
    void getAndSetAllotment() {
        DailyInventory entity = new DailyInventory();
        entity.setAllotment(100);
        assertThat(entity.getAllotment()).isEqualTo(100);
    }

    @Test
    void getAndSetOutOfService() {
        DailyInventory entity = new DailyInventory();
        entity.setOutOfService(10);
        assertThat(entity.getOutOfService()).isEqualTo(10);
    }

    @Test
    void getAndSetHeld() {
        DailyInventory entity = new DailyInventory();
        entity.setHeld(5);
        assertThat(entity.getHeld()).isEqualTo(5);
    }

    @Test
    void getAndSetConfirmed() {
        DailyInventory entity = new DailyInventory();
        entity.setConfirmed(3);
        assertThat(entity.getConfirmed()).isEqualTo(3);
    }

    @Test
    void getAndSetStopSell() {
        DailyInventory entity = new DailyInventory();
        entity.setStopSell(true);
        assertThat(entity.isStopSell()).isTrue();
        entity.setStopSell(false);
        assertThat(entity.isStopSell()).isFalse();
    }

    @Test
    void getAndSetVersion() {
        DailyInventory entity = new DailyInventory();
        entity.setVersion(5L);
        assertThat(entity.getVersion()).isEqualTo(5L);
    }

    @Test
    void getAndSetCreatedAt() {
        DailyInventory entity = new DailyInventory();
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void getAndSetUpdatedAt() {
        DailyInventory entity = new DailyInventory();
        Instant now = Instant.now();
        entity.setUpdatedAt(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void getAvailable_returnsCorrectCalculation() {
        DailyInventory entity = new DailyInventory();
        entity.setAllotment(100);
        entity.setOutOfService(10);
        entity.setHeld(5);
        entity.setConfirmed(3);
        assertThat(entity.getAvailable()).isEqualTo(82);
    }

    @Test
    void getAvailable_withZeroValues() {
        DailyInventory entity = new DailyInventory();
        entity.setAllotment(0);
        entity.setOutOfService(0);
        entity.setHeld(0);
        entity.setConfirmed(0);
        assertThat(entity.getAvailable()).isEqualTo(0);
    }

    @Test
    void getAvailable_withFullCommitment() {
        DailyInventory entity = new DailyInventory();
        entity.setAllotment(100);
        entity.setOutOfService(0);
        entity.setHeld(40);
        entity.setConfirmed(60);
        assertThat(entity.getAvailable()).isEqualTo(0);
    }
}
