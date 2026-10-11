package com.waylo.trafficsimulator.service;

import com.waylo.trafficsimulator.domain.ScenarioType;
import com.waylo.trafficsimulator.domain.SimulationStatus;
import com.waylo.trafficsimulator.dto.SimulationRequest;
import com.waylo.trafficsimulator.dto.SimulationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SimulationServiceImpl implements SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationServiceImpl.class);
    private final Map<String, SimulationResponse> simulations = new ConcurrentHashMap<>();

    @Override
    public SimulationResponse start(SimulationRequest request) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        SimulationResponse response = new SimulationResponse(
                id, request.scenarioType(), SimulationStatus.RUNNING,
                request.concurrentUsers(), request.durationSeconds(),
                0, 0, 0, 0.0, 0.0, 0.0, 0.0,
                Instant.now(), null
        );
        simulations.put(id, response);
        log.info("Simulation started: id={}, scenario={}, users={}", id, request.scenarioType(), request.concurrentUsers());
        return response;
    }

    @Override
    public SimulationResponse getStatus(String simulationId) {
        SimulationResponse response = simulations.get(simulationId);
        if (response == null) {
            throw new IllegalArgumentException("Simulation not found: " + simulationId);
        }
        return response;
    }

    @Override
    public List<SimulationResponse> listAll() {
        return List.copyOf(simulations.values());
    }

    @Override
    public SimulationResponse cancel(String simulationId) {
        SimulationResponse existing = getStatus(simulationId);
        SimulationResponse cancelled = new SimulationResponse(
                existing.simulationId(), existing.scenarioType(), SimulationStatus.CANCELLED,
                existing.concurrentUsers(), existing.durationSeconds(),
                existing.totalRequests(), existing.successfulRequests(), existing.failedRequests(),
                existing.avgResponseTimeMs(), existing.p95ResponseTimeMs(), existing.p99ResponseTimeMs(),
                existing.requestsPerSecond(), existing.startedAt(), Instant.now()
        );
        simulations.put(simulationId, cancelled);
        log.info("Simulation cancelled: id={}", simulationId);
        return cancelled;
    }
}
