package com.waylo.discoveryservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.security.user.password=test-only")
class DiscoveryServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
