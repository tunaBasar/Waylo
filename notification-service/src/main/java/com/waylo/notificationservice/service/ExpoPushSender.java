package com.waylo.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ExpoPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushSender.class);

    private final boolean enabled;
    private final RestClient client;

    public ExpoPushSender(@Value("${waylo.push.expo.enabled:false}") boolean enabled,
                          @Qualifier("expoRestClient") RestClient client) {
        this.enabled = enabled;
        this.client = client;
    }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> send(List<String> tokens, String title, String body) {
        List<Map<String, Object>> messages = tokens.stream()
                .map(t -> Map.<String, Object>of("to", t, "title", title == null ? "Waylo" : title, "body", body, "sound", "default"))
                .toList();
        Map<String, Object> response = client.post()
                .header("Accept", "application/json")
                .body(messages)
                .retrieve()
                .body(Map.class);
        Set<String> invalid = new HashSet<>();
        Object data = response == null ? null : response.get("data");
        if (data instanceof List<?> tickets) {
            for (int i = 0; i < tickets.size() && i < tokens.size(); i++) {
                if (tickets.get(i) instanceof Map<?, ?> ticket && "error".equals(ticket.get("status"))) {
                    Object details = ticket.get("details");
                    if (details instanceof Map<?, ?> d && "DeviceNotRegistered".equals(d.get("error"))) {
                        invalid.add(tokens.get(i));
                    } else {
                        log.warn("Expo push ticket error for one token: {}", ticket.get("message"));
                    }
                }
            }
        }
        return invalid;
    }
}
