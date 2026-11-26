package com.waylo.busservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** F77: the auth-service Feign client fails fast (2 s, same as hotel-service) instead of the default 10 s connect. */
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
