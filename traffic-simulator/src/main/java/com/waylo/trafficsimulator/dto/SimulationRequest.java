package com.waylo.trafficsimulator.dto;

import com.waylo.trafficsimulator.domain.ScenarioType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SimulationRequest(
        @NotNull(message = "Scenario type is required") ScenarioType scenarioType,
        @Min(value = 1, message = "Concurrent users must be at least 1") int concurrentUsers,
        @Min(value = 1, message = "Duration in seconds must be at least 1") int durationSeconds,
        @Min(value = 0, message = "Ramp-up seconds must be non-negative") int rampUpSeconds,
        String targetBaseUrl
) {
    public SimulationRequest {
        if (targetBaseUrl == null || targetBaseUrl.isBlank()) {
            targetBaseUrl = "http://localhost:8080";
        }
    }
}
