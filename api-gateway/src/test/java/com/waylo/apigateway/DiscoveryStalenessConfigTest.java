package com.waylo.apigateway;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** F67: stale Eureka/LB caches must stay short and the gateway retry must be GET-only. */
class DiscoveryStalenessConfigTest {

    private final String yml = read();

    private static String read() {
        try {
            return Files.readString(Path.of("src/main/resources/application.yml"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void retryFilterIsGetOnlyAndRetriesConnectFailures() {
        int at = yml.indexOf("- name: Retry");
        assertThat(at).isNotNegative();
        // The Retry block ends at the next default filter entry; the CORS block below lists POST/PUT on purpose.
        int end = yml.indexOf("\n            - ", at + 1);
        String block = yml.substring(at, end > 0 ? end : yml.indexOf("routes:", at));
        assertThat(block).contains("methods: GET\n");
        assertThat(block).contains("retries: 2");
        assertThat(block).contains("java.net.ConnectException");
        // series/statuses must be explicitly empty: the factory default (SERVER_ERROR) would retry every 5xx answer.
        assertThat(block).containsPattern("(?m)^\\s+series:\\s*$");
        assertThat(block).containsPattern("(?m)^\\s+statuses:\\s*$");
        assertThat(block).doesNotContain("POST").doesNotContain("PUT")
                .doesNotContain("PATCH").doesNotContain("DELETE");
    }

    @Test
    void discoveryCachesAreShort() {
        assertThat(yml).contains("registry-fetch-interval-seconds: 5");
        assertThat(yml).contains("ttl: 5s");
    }
}
