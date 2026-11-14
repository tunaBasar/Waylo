package com.waylo.observability;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void sanitizeKeepsSafeTokensOnly() {
        assertThat(RequestId.sanitize("abc-123")).isEqualTo("abc-123");
        assertThat(RequestId.sanitize("a".repeat(64))).isNotNull();
        assertThat(RequestId.sanitize("a".repeat(65))).isNull();
        assertThat(RequestId.sanitize("bad id")).isNull();
        assertThat(RequestId.sanitize("x\r\nInjected: 1")).isNull();
        assertThat(RequestId.sanitize("")).isNull();
        assertThat(RequestId.sanitizeOrGenerate("<script>")).matches("[0-9a-f-]{36}");
    }

    @Test
    void servletFilterSetsMdcDuringRequestAndEchoesHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(RequestId.HEADER, "req-42");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();
        new RequestIdFilter().doFilter(req, res, (rq, rs) -> seen.set(MDC.get(RequestId.MDC_KEY)));
        assertThat(seen.get()).isEqualTo("req-42");
        assertThat(res.getHeader(RequestId.HEADER)).isEqualTo("req-42");
        assertThat(MDC.get(RequestId.MDC_KEY)).isNull();
    }

    @Test
    void servletFilterReplacesUnsafeValue() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(RequestId.HEADER, "evil value");
        MockHttpServletResponse res = new MockHttpServletResponse();
        new RequestIdFilter().doFilter(req, res, (rq, rs) -> { });
        assertThat(res.getHeader(RequestId.HEADER)).matches("[0-9a-f-]{36}");
    }

    @Test
    void kafkaProducerAddsHeaderFromMdcAndListenerRestoresIt() {
        MDC.put(RequestId.MDC_KEY, "req-7");
        ProducerRecord<String, String> sent = new KafkaRequestIdInterceptors.Producer<String, String>()
                .onSend(new ProducerRecord<>("t", "k", "v"));
        assertThat(new String(sent.headers().lastHeader(RequestId.HEADER).value(), StandardCharsets.UTF_8)).isEqualTo("req-7");
        MDC.clear();

        ConsumerRecord<String, String> rec = new ConsumerRecord<>("t", 0, 0, "k", "v");
        rec.headers().add(RequestId.HEADER, "req-7".getBytes(StandardCharsets.UTF_8));
        KafkaRequestIdInterceptors.Listener<String, String> listener = new KafkaRequestIdInterceptors.Listener<>();
        listener.intercept(rec, null);
        assertThat(MDC.get(RequestId.MDC_KEY)).isEqualTo("req-7");
        listener.afterRecord(rec, null);
        assertThat(MDC.get(RequestId.MDC_KEY)).isNull();
    }

    @Test
    void producerKeepsExistingHeader() {
        MDC.put(RequestId.MDC_KEY, "other");
        ProducerRecord<String, String> r = new ProducerRecord<>("t", "k", "v");
        r.headers().add(RequestId.HEADER, "outbox-id".getBytes(StandardCharsets.UTF_8));
        new KafkaRequestIdInterceptors.Producer<String, String>().onSend(r);
        assertThat(r.headers().headers(RequestId.HEADER)).hasSize(1);
    }

    @Test
    void logPatternShowsRequestIdAndMasksEmails() {
        LoggerContext ctx = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        PatternLayout layout = new PatternLayout();
        layout.setContext(ctx);
        layout.setPattern(LoggingDefaultsEnvironmentPostProcessor.PATTERN.replace("%wEx", ""));
        layout.start();
        MDC.put(RequestId.MDC_KEY, "req-9");
        LoggingEvent e = new LoggingEvent("x", ctx.getLogger("t"), ch.qos.logback.classic.Level.INFO,
                "Welcome email sent to {}", null, new Object[]{"john.doe+tag@example.com"});
        String line = layout.doLayout(e);
        assertThat(line).contains("[req-9]").contains("j***@example.com").doesNotContain("john.doe");
    }
}
