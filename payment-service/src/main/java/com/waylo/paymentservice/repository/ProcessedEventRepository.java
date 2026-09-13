package com.waylo.paymentservice.repository;

import com.waylo.paymentservice.domain.ProcessedEvent;
import com.waylo.paymentservice.domain.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup);
}
