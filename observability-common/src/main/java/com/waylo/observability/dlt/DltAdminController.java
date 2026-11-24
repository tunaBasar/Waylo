package com.waylo.observability.dlt;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Admin-only DLT inspect/replay. Each consumer service registers one instance (see its KafkaConsumerConfig). The
 * gateway sets X-User-Role. Replay is safe because every consumer is idempotent through processed_events.
 * The "service" query parameter must match this service, which also lets the gateway route by Query predicate.
 */
@RestController
@RequestMapping("/api/admin/kafka-dlt")
public class DltAdminController {

    public record ReplayRequest(String topic, Integer partition, Long offset) {
    }

    private final DltBrowser browser;

    public DltAdminController(DltBrowser browser) {
        this.browser = browser;
    }

    @GetMapping
    public Map<String, Object> list(@RequestHeader(value = "X-User-Role", required = false) String role,
                                    @RequestParam("service") String service,
                                    @RequestParam("topic") String topic,
                                    @RequestParam(value = "limit", defaultValue = "50") int limit) {
        guard(role, service);
        List<Map<String, Object>> records = browser.peek(topic, limit);
        return Map.of("success", true, "data", records);
    }

    @PostMapping("/replay")
    public Map<String, Object> replay(@RequestHeader(value = "X-User-Role", required = false) String role,
                                      @RequestParam("service") String service,
                                      @RequestBody ReplayRequest req) {
        guard(role, service);
        if (req == null || req.topic() == null || req.partition() == null || req.offset() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topic, partition and offset are required");
        }
        try {
            String target = browser.replay(req.topic(), req.partition(), req.offset());
            return Map.of("success", true, "message", "Replayed to " + target);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    private void guard(String role, String service) {
        if (!"ADMIN".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        if (!browser.serviceName().equals(service)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown service " + service);
        }
    }
}
