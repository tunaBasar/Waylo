package com.waylo.trafficsimulator.dto;

import com.waylo.trafficsimulator.domain.ScenarioType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SimulationRequest(
        @NotNull(message = "Scenario type is required") ScenarioType scenarioType,
        @Min(value = 1, message = "Concurrent users must be at least 1")
        @Max(value = 5, message = "Concurrent users must be at most 5")
        int concurrentUsers,
        @Min(value = 1, message = "Duration in seconds must be at least 1")
        @Max(value = 60, message = "Duration must be at most 60 seconds")
        int durationSeconds,
        @Min(value = 0, message = "Ramp-up seconds must be non-negative")
        @Max(value = 30, message = "Ramp-up must be at most 30 seconds")
        int rampUpSeconds,
        String targetBaseUrl
) {
    public SimulationRequest {
        if (targetBaseUrl == null || targetBaseUrl.isBlank()) {
            targetBaseUrl = "http://localhost:8080";
        }
    }
}
