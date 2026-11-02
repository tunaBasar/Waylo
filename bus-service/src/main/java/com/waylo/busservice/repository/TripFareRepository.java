package com.waylo.busservice.repository;

import com.waylo.busservice.domain.TripFare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripFareRepository extends JpaRepository<TripFare, UUID> {

    List<TripFare> findByTripId(UUID tripId);

    List<TripFare> findByTripIdIn(Collection<UUID> tripIds);

    Optional<TripFare> findByTripIdAndFromSeqAndToSeq(UUID tripId, int fromSeq, int toSeq);
}
