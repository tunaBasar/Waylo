package com.waylo.busservice.repository;

import com.waylo.busservice.domain.TripStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TripStopRepository extends JpaRepository<TripStop, UUID> {

    List<TripStop> findByTripIdOrderBySeq(UUID tripId);

    List<TripStop> findByTripIdInOrderBySeq(Collection<UUID> tripIds);
}
