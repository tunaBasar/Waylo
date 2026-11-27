package com.waylo.busservice.repository;

import com.waylo.busservice.domain.Trip;
import com.waylo.busservice.domain.TripStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    List<Trip> findByOrganizationIdOrderByDepartureAtDesc(UUID organizationId);

    List<Trip> findByStatusAndDepartureAtBetweenOrderByDepartureAt(TripStatus status, Instant from, Instant to);

    boolean existsByBusIdAndStatus(UUID busId, TripStatus status);

    /** SELECT ... FOR UPDATE: all holds on one trip serialize here, so seat checks cannot race. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id")
    Optional<Trip> lockById(@Param("id") UUID id);

    /** Departures of one trip code in a window, any status (CANCELLED included), for schedule generation. */
    @Query("select t.departureAt from Trip t where t.organizationId = :orgId and t.code = :code"
            + " and t.departureAt between :from and :to")
    List<Instant> findDepartureTimes(@Param("orgId") UUID orgId, @Param("code") String code,
                                     @Param("from") Instant from, @Param("to") Instant to);

    long countByScheduleId(UUID scheduleId);

    @Query("select t.departureAt from Trip t where t.scheduleId = :scheduleId"
            + " and t.status = com.waylo.busservice.domain.TripStatus.PUBLISHED and t.departureAt >= :now"
            + " order by t.departureAt")
    List<Instant> findNextDepartures(@Param("scheduleId") UUID scheduleId, @Param("now") Instant now, Pageable page);
}
