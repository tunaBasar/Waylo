package com.waylo.hotelservice.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomStatusTest {

    @Test
    void allValues_exist() {
        RoomStatus[] values = RoomStatus.values();
        assertThat(values).hasSize(4);
        assertThat(values).containsExactly(RoomStatus.AVAILABLE, RoomStatus.RESERVED, RoomStatus.OCCUPIED, RoomStatus.MAINTENANCE);
    }

    @Test
    void valueOf_returnsCorrect() {
        assertThat(RoomStatus.valueOf("OCCUPIED")).isEqualTo(RoomStatus.OCCUPIED);
        assertThat(RoomStatus.valueOf("MAINTENANCE")).isEqualTo(RoomStatus.MAINTENANCE);
    }
}
