package com.waylo.carrentalservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F77: with auth-service down the admin car list waited 15 s on the default 10 s Feign connect timeout. The auth
 * client must fail fast (2 s, same as hotel-service) so the list answers quickly with "Durum bilinmiyor" (F59).
 */
class FeignTimeoutConfigTest {

    @Test
    void authServiceClientFailsFast() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties props = yaml.getObject();
        assertThat(props).isNotNull();
        String prefix = "spring.cloud.openfeign.client.config.auth-service.";
        assertThat(props.getProperty(prefix + "connect-timeout")).isEqualTo("2000");
        assertThat(props.getProperty(prefix + "read-timeout")).isEqualTo("2000");
    }
}
