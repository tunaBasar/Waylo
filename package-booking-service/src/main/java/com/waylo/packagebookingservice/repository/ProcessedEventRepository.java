package com.waylo.packagebookingservice.repository;

import com.waylo.packagebookingservice.domain.ProcessedEvent;
import com.waylo.packagebookingservice.domain.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup);
}
