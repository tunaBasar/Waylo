package com.waylo.trafficsimulator.service;

import com.waylo.trafficsimulator.domain.ScenarioType;
import com.waylo.trafficsimulator.domain.SimulationStatus;
import com.waylo.trafficsimulator.dto.SimulationRequest;
import com.waylo.trafficsimulator.dto.SimulationResponse;
import com.waylo.trafficsimulator.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SimulationServiceImpl implements SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationServiceImpl.class);
    private static final int MAX_SIMULATIONS = 20;
    private static final long REQUEST_DELAY_MS = 500;

    private final Map<String, SimulationResponse> simulations = new ConcurrentHashMap<>();
    /** Cancel flags of running simulations, read by the worker loops. */
    private final Map<String, java.util.concurrent.atomic.AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();
    private final WebClient webClient;
    private final String baseUrl;
    /** 5 workers (hard user limit) + 1 completion watcher, so a full 5-user run never queues a user. */
    private final ExecutorService executor = Executors.newFixedThreadPool(6);

    public SimulationServiceImpl(WebClient webClient,
                                 @org.springframework.beans.factory.annotation.Value("${waylo.simulator.base-url:http://localhost:8080}") String baseUrl) {
        this.webClient = webClient;
        this.baseUrl = baseUrl;
    }

    @Override
    public SimulationResponse start(SimulationRequest request) {
        if (request.concurrentUsers() < 1) {
            throw new IllegalArgumentException("Concurrent users must be at least 1");
        }
        if (request.concurrentUsers() > 5) {
            throw new IllegalArgumentException("Concurrent users must be at most 5");
        }
        if (request.durationSeconds() < 1) {
            throw new IllegalArgumentException("Duration must be at least 1 second");
        }
        if (request.durationSeconds() > 60) {
            throw new IllegalArgumentException("Duration must be at most 60 seconds");
        }
        if (request.rampUpSeconds() > 30) {
            throw new IllegalArgumentException("Ramp-up must be at most 30 seconds");
        }

        if (request.scenarioType() == ScenarioType.PACKAGE_BOOKING ||
                request.scenarioType() == ScenarioType.FULL_FLOW) {
            throw new BusinessRuleException("SCENARIO_DISABLED",
                    "writes are not simulated");
        }

        String id = UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();

        SimulationResponse resp = new SimulationResponse(
                id, request.scenarioType(), SimulationStatus.RUNNING,
                request.concurrentUsers(), request.durationSeconds(),
                0, 0, 0, 0.0, 0.0, 0.0, 0.0,
                Instant.now(), null
        );

        int users = request.concurrentUsers();
        int duration = request.durationSeconds();
        AtomicInteger total = new AtomicInteger(0);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<Double> latencies = new java.util.concurrent.CopyOnWriteArrayList<>();
        // Target is always the configured gateway; a client-supplied URL would let any caller aim traffic anywhere (SSRF).
        String targetUrl = baseUrl;

        simulations.put(id, resp);

        // Cap at 20 simulations by dropping oldest finished
        if (simulations.size() > MAX_SIMULATIONS) {
            String oldest = simulations.entrySet().stream()
                    .filter(e -> e.getValue().completedAt() != null)
                    .min(Map.Entry.comparingByKey())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (oldest != null) {
                simulations.remove(oldest);
            }
        }

        log.info("Simulation started: id={}, scenario={}, users={}, duration={}s",
                id, request.scenarioType(), users, duration);

        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        cancelFlags.put(id, cancelled);
        List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();

        for (int u = 0; u < users; u++) {
            int userIdx = u;
            AtomicInteger reqIdx = new AtomicInteger(0);
            CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                LocalDate baseDate = LocalDate.now();
                while (!cancelled.get() && System.currentTimeMillis() - now < (long) duration * 1000) {
                    long start = System.nanoTime();
                    int currentReq = reqIdx.getAndIncrement();
                    LocalDate date = baseDate.plusDays(currentReq % 7 + 1);
                    LocalDateTime pickupAt = LocalDateTime.of(baseDate.plusDays(currentReq % 7 + 1), java.time.LocalTime.of(10, 0, 0));
                    LocalDateTime returnAt = LocalDateTime.of(baseDate.plusDays(currentReq % 7 + 3), java.time.LocalTime.of(10, 0, 0));

                    try {
                        performScenario(request.scenarioType(), currentReq, date, pickupAt, returnAt,
                                targetUrl, total, success, failed, latencies);
                    } catch (Exception e) {
                        failed.incrementAndGet();
                        latencies.add((double) ((System.nanoTime() - start) / 1_000_000));
                        log.debug("Simulation {} user {} request {} error: {}", id, userIdx, currentReq, e.getMessage());
                    }
                    try {
                        Thread.sleep(REQUEST_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        cancelled.set(true);
                    }
                }
            }, executor);
            futures.add(f);
        }

        // When all workers finish, mark simulation as completed
        CompletableFuture.runAsync(() -> {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                cancelFlags.remove(id);
                if (!cancelled.get()) {
                    completed(id, total, success, failed, latencies, Instant.now());
                } else {
                    cancelled(id, total, success, failed, latencies, Instant.now());
                }
            });
        }, executor);

        return resp;
    }

    private void performScenario(ScenarioType scenario, int reqIdx, LocalDate date,
                                  LocalDateTime pickupAt, LocalDateTime returnAt,
                                  String baseUrl, AtomicInteger total,
                                  AtomicInteger success, AtomicInteger failed,
                                  List<Double> latencies) {
        switch (scenario) {
            case FLIGHT_SEARCH -> flightSearch(reqIdx, date, baseUrl, total, success, failed, latencies);
            case CAR_SEARCH -> carSearch(reqIdx, pickupAt, returnAt, baseUrl, total, success, failed, latencies);
            case HOTEL_SEARCH -> hotelSearch(reqIdx, date, baseUrl, total, success, failed, latencies);
            case MIXED -> {
                int mod = reqIdx % 3;
                if (mod == 0) flightSearch(reqIdx, date, baseUrl, total, success, failed, latencies);
                else if (mod == 1) carSearch(reqIdx, pickupAt, returnAt, baseUrl, total, success, failed, latencies);
                else hotelSearch(reqIdx, date, baseUrl, total, success, failed, latencies);
            }
            default -> { /* should not reach here */ }
        }
    }

    private void flightSearch(int reqIdx, LocalDate date, String baseUrl,
                              AtomicInteger total, AtomicInteger success, AtomicInteger failed,
                              List<Double> latencies) {
        String[][] pairs = {
                {"IST", "ESB"}, {"ESB", "IST"},
                {"IST", "ADB"}, {"ADB", "IST"},
                {"ESB", "ADB"}, {"ADB", "ESB"},
                {"IST", "AYT"}, {"AYT", "IST"},
        };
        String[] pair = pairs[reqIdx % pairs.length];
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/api/flight-offers")
                .queryParam("from", pair[0])
                .queryParam("to", pair[1])
                .queryParam("date", date.toString())
                .queryParam("adults", 1)
                .build().toUriString();

        executeRequest(url, total, success, failed, latencies);
    }

    private void carSearch(int reqIdx, LocalDateTime pickupAt, LocalDateTime returnAt,
                           String baseUrl, AtomicInteger total, AtomicInteger success, AtomicInteger failed,
                           List<Double> latencies) {
        String branchesUrl = baseUrl + "/api/car-branches";
        try {
            String body = webClient.get().uri(branchesUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<String> branches = parseBranchIds(body);
            if (branches.isEmpty()) {
                total.incrementAndGet();
                success.incrementAndGet();
                latencies.add(0.0);
                return;
            }

            String url = UriComponentsBuilder.fromUriString(baseUrl + "/api/car-offers")
                    .queryParam("pickupBranchId", branches.get(reqIdx % branches.size()))
                    .queryParam("returnBranchId", branches.get((reqIdx + 1) % branches.size()))
                    .queryParam("pickupAt", pickupAt.toString())
                    .queryParam("returnAt", returnAt.toString())
                    .queryParam("driverAge", 30)
                    .build().toUriString();

            executeRequest(url, total, success, failed, latencies);
        } catch (Exception e) {
            total.incrementAndGet();
            failed.incrementAndGet();
            latencies.add(0.0);
        }
    }

    private List<String> parseBranchIds(String body) {
        var ids = new java.util.ArrayList<String>();
        var regex = java.util.regex.Pattern.compile("\"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\"");
        var m = regex.matcher(body);
        while (m.find()) {
            ids.add(m.group());
        }
        return ids;
    }

    private void hotelSearch(int reqIdx, LocalDate date, String baseUrl,
                             AtomicInteger total, AtomicInteger success, AtomicInteger failed,
                             List<Double> latencies) {
        String[] cities = {"Istanbul", "Antalya", "Cappadocia", "Bodrum", "Izmir"};
        LocalDate checkIn = date;
        LocalDate checkOut = date.plusDays(1);
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/api/hotels/search")
                .queryParam("city", cities[reqIdx % cities.length])
                .queryParam("checkIn", checkIn.toString())
                .queryParam("checkOut", checkOut.toString())
                .queryParam("rooms", 1)
                .queryParam("adults", 1)
                .build().toUriString();

        executeRequest(url, total, success, failed, latencies);
    }

    private void executeRequest(String url, AtomicInteger total, AtomicInteger success,
                                AtomicInteger failed, List<Double> latencies) {
        long start = System.nanoTime();
        try {
            webClient.get().uri(url)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            long ms = (System.nanoTime() - start) / 1_000_000;
            total.incrementAndGet();
            success.incrementAndGet();
            latencies.add((double) ms);
        } catch (Exception e) {
            total.incrementAndGet();
            failed.incrementAndGet();
            latencies.add((double) ((System.nanoTime() - start) / 1_000_000));
        }
    }

    private void completed(String id, AtomicInteger total, AtomicInteger success,
                           AtomicInteger failed, List<Double> latencies, Instant completedAt) {
        SimulationResponse prev = simulations.get(id);
        SimulationResponse updated = new SimulationResponse(
                id, prev.scenarioType(), SimulationStatus.COMPLETED,
                prev.concurrentUsers(), prev.durationSeconds(),
                total.longValue(), success.longValue(), failed.longValue(),
                calcAvg(latencies), calcP95(latencies), calcP99(latencies),
                calcRps(total.longValue(), prev.startedAt(), completedAt),
                prev.startedAt(), completedAt
        );
        simulations.put(id, updated);
        log.info("Simulation completed: id={}, total={}, success={}, failed={}",
                id, total.get(), success.get(), failed.get());
    }

    private void cancelled(String id, AtomicInteger total, AtomicInteger success,
                           AtomicInteger failed, List<Double> latencies, Instant completedAt) {
        SimulationResponse prev = simulations.get(id);
        SimulationResponse updated = new SimulationResponse(
                id, prev.scenarioType(), SimulationStatus.CANCELLED,
                prev.concurrentUsers(), prev.durationSeconds(),
                total.longValue(), success.longValue(), failed.longValue(),
                calcAvg(latencies), calcP95(latencies), calcP99(latencies),
                calcRps(total.longValue(), prev.startedAt(), completedAt),
                prev.startedAt(), completedAt
        );
        simulations.put(id, updated);
        log.info("Simulation cancelled: id={}", id);
    }

    private double calcAvg(List<Double> latencies) {
        if (latencies.isEmpty()) return 0.0;
        return latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double calcP95(List<Double> latencies) {
        if (latencies.isEmpty()) return 0.0;
        if (latencies.size() == 1) return latencies.get(0);
        List<Double> sorted = latencies.stream().sorted().toList();
        return percentile(sorted, 0.95);
    }

    private double calcP99(List<Double> latencies) {
        if (latencies.isEmpty()) return 0.0;
        if (latencies.size() == 1) return latencies.get(0);
        List<Double> sorted = latencies.stream().sorted().toList();
        return percentile(sorted, 0.99);
    }

    private double percentile(List<Double> sorted, double p) {
        double rank = p * (sorted.size() - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) return sorted.get(lo);
        return sorted.get(lo) + (sorted.get(hi) - sorted.get(lo)) * (rank - lo);
    }

    private double calcRps(long totalRequests, Instant started, Instant finished) {
        double seconds = java.time.Duration.between(started, finished).getSeconds();
        if (seconds <= 0) return 0.0;
        return (double) totalRequests / seconds;
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
        if (existing.status() == SimulationStatus.COMPLETED || existing.status() == SimulationStatus.CANCELLED) {
            return existing;
        }
        // Stop the worker loops; the completion watcher then records the final CANCELLED metrics.
        java.util.concurrent.atomic.AtomicBoolean flag = cancelFlags.get(simulationId);
        if (flag != null) {
            flag.set(true);
        }
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
