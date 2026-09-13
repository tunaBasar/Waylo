package com.waylo.packagebookingsagaorchestrator.repository;

import com.waylo.packagebookingsagaorchestrator.domain.ProcessedEvent;
import com.waylo.packagebookingsagaorchestrator.domain.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup);
}
