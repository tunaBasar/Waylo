package com.waylo.configservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.security.user.password=test-only")
class ConfigServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
