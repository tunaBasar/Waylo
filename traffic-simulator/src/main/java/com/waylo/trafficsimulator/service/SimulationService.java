package com.waylo.trafficsimulator.service;

import com.waylo.trafficsimulator.dto.SimulationRequest;
import com.waylo.trafficsimulator.dto.SimulationResponse;

import java.util.List;

public interface SimulationService {
    SimulationResponse start(SimulationRequest request);
    SimulationResponse getStatus(String simulationId);
    List<SimulationResponse> listAll();
    SimulationResponse cancel(String simulationId);
}
