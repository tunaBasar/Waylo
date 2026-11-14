package com.waylo.hotelservice.repository;

import com.waylo.hotelservice.domain.ReviewModerationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewModerationEventRepository extends JpaRepository<ReviewModerationEvent, UUID> {
}
