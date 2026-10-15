package com.waylo.hotelservice.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomTypeTest {

    @Test
    void allValues_exist() {
        RoomType[] values = RoomType.values();
        assertThat(values).hasSize(5);
        assertThat(values).containsExactly(RoomType.SINGLE, RoomType.DOUBLE, RoomType.SUITE, RoomType.DELUXE, RoomType.FAMILY);
    }

    @Test
    void valueOf_returnsCorrect() {
        assertThat(RoomType.valueOf("SINGLE")).isEqualTo(RoomType.SINGLE);
        assertThat(RoomType.valueOf("DELUXE")).isEqualTo(RoomType.DELUXE);
    }
}
