package com.waylo.hotelservice.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoomTest {

    @Test
    void getAndSetHotel() {
        Room room = new Room();
        Hotel hotel = new Hotel();
        room.setHotel(hotel);
        assertThat(room.getHotel()).isEqualTo(hotel);
    }

    @Test
    void getAndSetRoomNumber() {
        Room room = new Room();
        room.setRoomNumber("101");
        assertThat(room.getRoomNumber()).isEqualTo("101");
    }

    @Test
    void getAndSetRoomType() {
        Room room = new Room();
        room.setRoomType(RoomType.SINGLE);
        assertThat(room.getRoomType()).isEqualTo(RoomType.SINGLE);
    }

    @Test
    void getStatusDefaultsAvailable() {
        Room room = new Room();
        assertThat(room.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
    }

    @Test
    void getAndSetStatus() {
        Room room = new Room();
        room.setStatus(RoomStatus.RESERVED);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.RESERVED);
    }

    @Test
    void getAndSetCapacity() {
        Room room = new Room();
        room.setCapacity(4);
        assertThat(room.getCapacity()).isEqualTo(4);
    }

    @Test
    void getAndSetPricePerNight() {
        Room room = new Room();
        room.setPricePerNight(java.math.BigDecimal.valueOf(250));
        assertThat(room.getPricePerNight()).isEqualTo(java.math.BigDecimal.valueOf(250));
    }

    @Test
    void getAndSetFloor() {
        Room room = new Room();
        room.setFloor(5);
        assertThat(room.getFloor()).isEqualTo(5);
    }

    @Test
    void getAndSetDescription() {
        Room room = new Room();
        room.setDescription("Deluxe suite");
        assertThat(room.getDescription()).isEqualTo("Deluxe suite");
    }
}
