package com.waylo.trafficsimulator.dto;

import com.waylo.trafficsimulator.domain.ScenarioType;
import com.waylo.trafficsimulator.domain.SimulationStatus;
import java.time.Instant;

public record SimulationResponse(
        String simulationId,
        ScenarioType scenarioType,
        SimulationStatus status,
        int concurrentUsers,
        int durationSeconds,
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        double avgResponseTimeMs,
        double p95ResponseTimeMs,
        double p99ResponseTimeMs,
        double requestsPerSecond,
        Instant startedAt,
        Instant completedAt
) {}
