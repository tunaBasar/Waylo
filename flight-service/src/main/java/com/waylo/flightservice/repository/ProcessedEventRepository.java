package com.waylo.flightservice.repository;

import com.waylo.flightservice.domain.ProcessedEvent;
import com.waylo.flightservice.domain.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup);
}
