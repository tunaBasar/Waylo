package com.waylo.apigateway;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The three kafka-dlt routes must be declared before the auth-service route that owns /api/admin/**. */
class KafkaDltRoutesConfigTest {

    @Test
    void kafkaDltRoutesPrecedeAuthServiceRoute() throws Exception {
        List<String> lines = Files.readAllLines(Path.of("src/main/resources/application.yml"));
        int auth = lines.indexOf("            - id: auth-service");
        assertThat(auth).isPositive();
        String yml = String.join("\n", lines.subList(0, auth));
        for (String svc : List.of("crm", "notification", "flight-seat-inventory")) {
            int id = yml.indexOf("- id: kafka-dlt-" + svc + "\n");
            assertThat(id).as("route kafka-dlt-" + svc).isNotNegative();
            String block = yml.substring(id, yml.indexOf("- id:", id + 5) < 0 ? yml.length() : yml.indexOf("- id:", id + 5));
            assertThat(block).contains("uri: lb://" + svc + "-service");
            assertThat(block).contains("Path=/api/admin/kafka-dlt,/api/admin/kafka-dlt/replay");
            assertThat(block).contains("Query=service," + svc + "-service");
        }
    }
}
