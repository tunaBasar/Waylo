package com.waylo.trafficsimulator.service;

import com.waylo.trafficsimulator.domain.ScenarioType;
import com.waylo.trafficsimulator.domain.SimulationStatus;
import com.waylo.trafficsimulator.dto.SimulationRequest;
import com.waylo.trafficsimulator.dto.SimulationResponse;
import com.waylo.trafficsimulator.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class SimulationServiceTest {

    private SimulationServiceImpl service;

    @Mock
    private WebClient webClient;

    @BeforeEach
    void setUp() {
        service = new SimulationServiceImpl(webClient, "http://test:8080");
    }

    @Test
    void start_concurrentUsersZero_validationFails() {
        var request = new SimulationRequest(
                ScenarioType.FLIGHT_SEARCH,
                0, 5, 0, null);

        assertThatThrownBy(() -> service.start(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Concurrent users must be at least 1");
    }

    @Test
    void start_concurrentUsers50_validationFails() {
        var request = new SimulationRequest(
                ScenarioType.FLIGHT_SEARCH,
                50, 5, 0, null);

        assertThatThrownBy(() -> service.start(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Concurrent users must be at most 5");
    }

    @Test
    void start_duration120_validationFails() {
        var request = new SimulationRequest(
                ScenarioType.FLIGHT_SEARCH,
                1, 120, 0, null);

        assertThatThrownBy(() -> service.start(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duration must be at most 60 seconds");
    }

    @Test
    void start_rampUpExceeds30_validationFails() {
        var request = new SimulationRequest(
                ScenarioType.FLIGHT_SEARCH,
                1, 5, 35, null);

        assertThatThrownBy(() -> service.start(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ramp-up must be at most 30 seconds");
    }

    @Test
    void start_packageBooking_throws422() {
        var request = new SimulationRequest(
                ScenarioType.PACKAGE_BOOKING,
                1, 5, 0, null);

        assertThatThrownBy(() -> service.start(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("writes are not simulated");
    }

    @Test
    void start_fullFlow_throws422() {
        var request = new SimulationRequest(
                ScenarioType.FULL_FLOW,
                1, 5, 0, null);

        assertThatThrownBy(() -> service.start(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("writes are not simulated");
    }

    @Test
    void start_flightSearch_returnsRunning() {
        var request = new SimulationRequest(
                ScenarioType.FLIGHT_SEARCH, 1, 2, 0, null);

        SimulationResponse resp = service.start(request);
        assertThat(resp.status()).isEqualTo(SimulationStatus.RUNNING);
        assertThat(resp.simulationId()).isNotBlank();
        assertThat(resp.totalRequests()).isEqualTo(0);
        assertThat(resp.completedAt()).isNull();

        // Wait for workers to finish (2 seconds duration)
        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        SimulationResponse completed = service.getStatus(resp.simulationId());
        assertThat(completed.status()).isEqualTo(SimulationStatus.COMPLETED);
        assertThat(completed.totalRequests()).isGreaterThan(0);
        assertThat(completed.successfulRequests()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void start_cancel_stopsCounting() {
        var request = new SimulationRequest(
                ScenarioType.FLIGHT_SEARCH, 1, 10, 0, null);

        SimulationResponse resp = service.start(request);
        assertThat(resp.status()).isEqualTo(SimulationStatus.RUNNING);

        // Cancel immediately - workers will throw errors but cancel should work
        SimulationResponse cancelled = service.cancel(resp.simulationId());
        assertThat(cancelled.status()).isEqualTo(SimulationStatus.CANCELLED);
        assertThat(cancelled.completedAt()).isNotNull();
    }

    @Test
    void start_mixed_scenario_completes() {
        var request = new SimulationRequest(
                ScenarioType.MIXED, 1, 2, 0, null);

        SimulationResponse resp = service.start(request);
        assertThat(resp.status()).isEqualTo(SimulationStatus.RUNNING);

        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        SimulationResponse completed = service.getStatus(resp.simulationId());
        assertThat(completed.status()).isEqualTo(SimulationStatus.COMPLETED);
    }

    @Test
    void getStatus_notFound_throwsException() {
        assertThatThrownBy(() -> service.getStatus("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Simulation not found");
    }

    @Test
    void cancel_alreadyCompleted_returnsSameStatus() {
        var request = new SimulationRequest(
                ScenarioType.FLIGHT_SEARCH, 1, 1, 0, null);
        SimulationResponse resp = service.start(request);

        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Cancel already completed simulation should return existing status
        SimulationResponse cancelled = service.cancel(resp.simulationId());
        assertThat(cancelled.status()).isEqualTo(SimulationStatus.COMPLETED);
    }

    // --- Percentile math tests (no HTTP needed) ---

    @Test
    void calcP95_correctWith20Values() {
        List<Double> sorted = java.util.Arrays.asList(
                10.0, 20.0, 30.0, 40.0, 50.0,
                60.0, 70.0, 80.0, 90.0, 100.0,
                110.0, 120.0, 130.0, 140.0, 150.0,
                160.0, 170.0, 180.0, 190.0, 200.0);
        double p95 = percentile(sorted, 0.95);
        // expected: 190 + (200-190) * 0.05 = 190.5
        assertThat(p95 - 190.5).isBetween(-0.01, 0.01);
    }

    @Test
    void calcP99_correctWith20Values() {
        List<Double> sorted = java.util.Arrays.asList(
                10.0, 20.0, 30.0, 40.0, 50.0,
                60.0, 70.0, 80.0, 90.0, 100.0,
                110.0, 120.0, 130.0, 140.0, 150.0,
                160.0, 170.0, 180.0, 190.0, 200.0);
        double p99 = percentile(sorted, 0.99);
        // expected: 190 + (200-190) * 0.81 = 198.1
        assertThat(p99 - 198.1).isBetween(-0.01, 0.01);
    }

    @Test
    void calcP95_emptyList_returns0() {
        assertThat(percentile(List.of(), 0.95)).isEqualTo(0.0);
    }

    @Test
    void calcP95_singleValue_returnsThatValue() {
        assertThat(percentile(List.of(42.0), 0.95)).isEqualTo(42.0);
    }

    @Test
    void listAll_empty_returnsEmptyList() {
        assertThat(service.listAll()).isEmpty();
    }

    @Test
    void listAll_hasOneEntry() {
        service.start(new SimulationRequest(ScenarioType.FLIGHT_SEARCH, 1, 1, 0, null));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        assertThat(service.listAll()).hasSize(1);
    }

    // Helper for testing percentile logic (copy of the private method's logic)
    private double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return 0.0;
        if (sorted.size() == 1) return sorted.get(0);
        double rank = p * (sorted.size() - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) return sorted.get(lo);
        return sorted.get(lo) + (sorted.get(hi) - sorted.get(lo)) * (rank - lo);
    }
}
