package com.waylo.busservice.repository;

import com.waylo.busservice.domain.TripScheduleStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TripScheduleStopRepository extends JpaRepository<TripScheduleStop, UUID> {

    List<TripScheduleStop> findByScheduleIdOrderBySeq(UUID scheduleId);

    List<TripScheduleStop> findByScheduleIdInOrderBySeq(Collection<UUID> scheduleIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TripScheduleStop s where s.scheduleId = :scheduleId")
    void deleteAllByScheduleId(@Param("scheduleId") UUID scheduleId);
}
