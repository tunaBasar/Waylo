package com.waylo.hotelservice.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DailyInventoryRepositoryTest {

    @Test
    void findByHotelIdAndRoomTypeIdsAndDateRangeWithLock_hasPessimisticWriteLock() throws Exception {
        Method method = DailyInventoryRepository.class.getMethod(
                "findByHotelIdAndRoomTypeIdsAndDateRangeWithLock",
                UUID.class, List.class, LocalDate.class, LocalDate.class);

        Lock lockAnnotation = method.getAnnotation(Lock.class);
        assertThat(lockAnnotation).isNotNull();
        assertThat(lockAnnotation.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void findByHotelIdAndRoomTypeIdsAndDateRangeWithLock_usesPlainJPQL() throws Exception {
        Method method = DailyInventoryRepository.class.getMethod(
                "findByHotelIdAndRoomTypeIdsAndDateRangeWithLock",
                UUID.class, List.class, LocalDate.class, LocalDate.class);

        org.springframework.data.jpa.repository.Query query = method.getAnnotation(org.springframework.data.jpa.repository.Query.class);
        assertThat(query).isNotNull();

        String jpql = query.value();
        // Must NOT contain invalid WITH FOR UPDATE
        assertThat(jpql).doesNotContain("WITH FOR UPDATE");
        assertThat(jpql).contains("SELECT d FROM DailyInventory d");
        assertThat(jpql).contains("ORDER BY d.roomTypeId, d.stayDate");
    }
}
