package com.waylo.notificationservice.repository;

import com.waylo.notificationservice.domain.ProcessedEvent;
import com.waylo.notificationservice.domain.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup);
}
