package com.waylo.observability;

import org.slf4j.MDC;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/** Request id propagation for servlet services, Feign clients and Kafka. Each part only activates when its library is present. */
@AutoConfiguration
public class ObservabilityAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = {"jakarta.servlet.Filter", "org.springframework.boot.web.servlet.FilterRegistrationBean"})
    static class Servlet {
        @Bean
        FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
            FilterRegistrationBean<RequestIdFilter> reg = new FilterRegistrationBean<>(new RequestIdFilter());
            reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return reg;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    static class Feign {
        /** Global bean: every Feign client context inherits it from the parent context. */
        @Bean
        feign.RequestInterceptor requestIdFeignInterceptor() {
            return template -> {
                String id = MDC.get(RequestId.MDC_KEY);
                if (id != null) {
                    template.removeHeader(RequestId.HEADER);
                    template.header(RequestId.HEADER, id);
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
    static class Kafka {
        /** Services build their own templates and listener factories, so the interceptors are attached after creation. */
        @Bean
        static BeanPostProcessor requestIdKafkaPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof KafkaTemplate<?, ?> t) {
                        t.setProducerInterceptor(new KafkaRequestIdInterceptors.Producer<>());
                    } else if (bean instanceof AbstractKafkaListenerContainerFactory<?, ?, ?> f && f.getRecordInterceptor() == null) {
                        f.setRecordInterceptor(new KafkaRequestIdInterceptors.Listener<>());
                    }
                    return bean;
                }
            };
        }
    }
}
