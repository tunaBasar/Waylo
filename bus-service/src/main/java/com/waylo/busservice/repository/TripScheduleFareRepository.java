package com.waylo.busservice.repository;

import com.waylo.busservice.domain.TripScheduleFare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TripScheduleFareRepository extends JpaRepository<TripScheduleFare, UUID> {

    List<TripScheduleFare> findByScheduleId(UUID scheduleId);

    List<TripScheduleFare> findByScheduleIdIn(Collection<UUID> scheduleIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TripScheduleFare f where f.scheduleId = :scheduleId")
    void deleteAllByScheduleId(@Param("scheduleId") UUID scheduleId);
}
