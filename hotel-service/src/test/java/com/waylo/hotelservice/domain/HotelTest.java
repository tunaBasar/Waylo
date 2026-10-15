package com.waylo.hotelservice.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HotelTest {

    @Test
    void getAndSetId() {
        Hotel hotel = new Hotel();
        UUID id = UUID.randomUUID();
        hotel.setId(id);
        assertThat(hotel.getId()).isEqualTo(id);
    }

    @Test
    void getAndSetName() {
        Hotel hotel = new Hotel();
        hotel.setName("Grand Hotel");
        assertThat(hotel.getName()).isEqualTo("Grand Hotel");
    }

    @Test
    void getAndSetCity() {
        Hotel hotel = new Hotel();
        hotel.setCity("Istanbul");
        assertThat(hotel.getCity()).isEqualTo("Istanbul");
    }

    @Test
    void getAndSetCountry() {
        Hotel hotel = new Hotel();
        hotel.setCountry("Turkey");
        assertThat(hotel.getCountry()).isEqualTo("Turkey");
    }

    @Test
    void getAndSetAddress() {
        Hotel hotel = new Hotel();
        hotel.setAddress("123 Main St");
        assertThat(hotel.getAddress()).isEqualTo("123 Main St");
    }

    @Test
    void getAndSetStarRating() {
        Hotel hotel = new Hotel();
        hotel.setStarRating(5);
        assertThat(hotel.getStarRating()).isEqualTo(5);
    }

    @Test
    void getAndSetDescription() {
        Hotel hotel = new Hotel();
        hotel.setDescription("Luxury hotel");
        assertThat(hotel.getDescription()).isEqualTo("Luxury hotel");
    }

    @Test
    void getAndSetLatitude() {
        Hotel hotel = new Hotel();
        BigDecimal lat = BigDecimal.valueOf(41.0082);
        hotel.setLatitude(lat);
        assertThat(hotel.getLatitude()).isEqualTo(lat);
    }

    @Test
    void getAndSetLongitude() {
        Hotel hotel = new Hotel();
        BigDecimal lon = BigDecimal.valueOf(28.9784);
        hotel.setLongitude(lon);
        assertThat(hotel.getLongitude()).isEqualTo(lon);
    }

    @Test
    void isActiveDefaultsTrue_and_setActive() {
        Hotel hotel = new Hotel();
        assertThat(hotel.isActive()).isTrue();

        hotel.setActive(false);
        assertThat(hotel.isActive()).isFalse();
    }

    @Test
    void getAndSetOrganizationId() {
        Hotel hotel = new Hotel();
        UUID orgId = UUID.randomUUID();
        hotel.setOrganizationId(orgId);
        assertThat(hotel.getOrganizationId()).isEqualTo(orgId);
    }

    @Test
    void getAndSetRooms() {
        Hotel hotel = new Hotel();
        List<Room> rooms = List.of(new Room());
        hotel.setRooms(rooms);
        assertThat(hotel.getRooms()).isEqualTo(rooms);
    }

    @Test
    void roomsInitializedToEmptyList() {
        Hotel hotel = new Hotel();
        assertThat(hotel.getRooms()).isNotNull();
        assertThat(hotel.getRooms()).isEmpty();
    }
}
