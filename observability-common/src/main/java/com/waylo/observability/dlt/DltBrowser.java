package com.waylo.observability.dlt;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Bounded peek into {@code <topic>.DLT} and single-record replay to the original topic. Payloads are masked and truncated. */
public class DltBrowser {

    static final int MAX_LIMIT = 100;
    static final int MAX_PAYLOAD = 300;
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9_+-])[A-Za-z0-9._+-]*@([A-Za-z0-9-]+[.])");
    private static final Duration POLL = Duration.ofSeconds(3);

    private final String bootstrapServers;
    private final String serviceName;

    public DltBrowser(String bootstrapServers, String serviceName) {
        this.bootstrapServers = bootstrapServers;
        this.serviceName = serviceName;
    }

    public String serviceName() {
        return serviceName;
    }

    /** a.user@x.com -> a***@x.com, then cut to MAX_PAYLOAD chars. */
    public static String maskAndTruncate(String s) {
        if (s == null) return null;
        String masked = EMAIL.matcher(s).replaceAll("$1***@$2");
        return masked.length() > MAX_PAYLOAD ? masked.substring(0, MAX_PAYLOAD) + "...(truncated)" : masked;
    }

    public static String dltTopic(String topic) {
        return topic.endsWith(DltErrorHandlers.DLT_SUFFIX) ? topic : topic + DltErrorHandlers.DLT_SUFFIX;
    }

    private Map<String, Object> consumerProps() {
        Map<String, Object> p = new HashMap<>();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        p.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        p.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, MAX_LIMIT);
        return p;
    }

    public List<Map<String, Object>> peek(String topic, int limit) {
        int max = Math.max(1, Math.min(limit, MAX_LIMIT));
        String dlt = dltTopic(topic);
        List<Map<String, Object>> out = new ArrayList<>();
        try (KafkaConsumer<byte[], byte[]> c = new KafkaConsumer<>(consumerProps())) {
            List<PartitionInfo> infos = c.partitionsFor(dlt, Duration.ofSeconds(5));
            if (infos == null || infos.isEmpty()) return out;
            List<TopicPartition> tps = infos.stream().map(i -> new TopicPartition(dlt, i.partition())).toList();
            c.assign(tps);
            Map<TopicPartition, Long> begin = c.beginningOffsets(tps);
            Map<TopicPartition, Long> end = c.endOffsets(tps);
            long remaining = 0;
            for (TopicPartition tp : tps) {
                long start = Math.max(begin.get(tp), end.get(tp) - max);
                c.seek(tp, start);
                remaining += end.get(tp) - start;
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (remaining > 0 && out.size() < max && System.nanoTime() < deadline) {
                for (ConsumerRecord<byte[], byte[]> r : c.poll(POLL)) {
                    remaining--;
                    if (out.size() < max && belongsToService(r)) out.add(describe(r));
                }
            }
        }
        return out;
    }

    /** Several services read the same topic, so the DLT is shared. The original consumer group header tells them apart. */
    private boolean belongsToService(ConsumerRecord<byte[], byte[]> r) {
        String group = str(r.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP));
        return group == null || serviceName == null || group.startsWith(serviceName);
    }

    Map<String, Object> describe(ConsumerRecord<byte[], byte[]> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dltTopic", r.topic());
        m.put("dltPartition", r.partition());
        m.put("dltOffset", r.offset());
        m.put("key", maskAndTruncate(r.key() == null ? null : new String(r.key(), StandardCharsets.UTF_8)));
        m.put("exceptionClass", str(r.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)));
        m.put("exceptionMessage", maskAndTruncate(str(r.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE))));
        m.put("originalTopic", str(r.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC)));
        m.put("originalPartition", intOf(r.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_PARTITION)));
        m.put("originalOffset", longOf(r.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET)));
        m.put("consumerGroup", str(r.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP)));
        m.put("timestamp", r.timestamp());
        m.put("payload", maskAndTruncate(r.value() == null ? null : new String(r.value(), StandardCharsets.UTF_8)));
        return m;
    }

    /** Republishes one DLT record to its original topic (same key, value and headers minus the DLT ones). Returns the target topic. */
    public String replay(String topic, int partition, long offset) {
        String dlt = dltTopic(topic);
        ConsumerRecord<byte[], byte[]> found = null;
        try (KafkaConsumer<byte[], byte[]> c = new KafkaConsumer<>(consumerProps())) {
            TopicPartition tp = new TopicPartition(dlt, partition);
            c.assign(List.of(tp));
            c.seek(tp, offset);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (found == null && System.nanoTime() < deadline) {
                for (ConsumerRecord<byte[], byte[]> r : c.poll(POLL)) {
                    if (r.offset() == offset) {
                        found = r;
                        break;
                    }
                }
                if (found == null && c.position(tp) > offset) break;
            }
        }
        if (found == null) throw new IllegalArgumentException("No DLT record at " + dlt + "-" + partition + "@" + offset);
        if (!belongsToService(found)) throw new IllegalArgumentException("DLT record belongs to another service");
        String target = str(found.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC));
        if (target == null || target.isBlank()) throw new IllegalArgumentException("DLT record has no original topic header");

        ProducerRecord<byte[], byte[]> out = new ProducerRecord<>(target, null, found.key(), found.value());
        for (Header h : found.headers()) {
            if (!h.key().startsWith("kafka_dlt-")) out.headers().add(h);
        }
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        try (KafkaProducer<byte[], byte[]> p = new KafkaProducer<>(props, new ByteArraySerializer(), new ByteArraySerializer())) {
            p.send(out).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Replay publish failed: " + e.getMessage(), e);
        }
        return target;
    }

    private static String str(Header h) {
        return h == null || h.value() == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private static Integer intOf(Header h) {
        return h == null || h.value() == null || h.value().length != 4 ? null : ByteBuffer.wrap(h.value()).getInt();
    }

    private static Long longOf(Header h) {
        return h == null || h.value() == null || h.value().length != 8 ? null : ByteBuffer.wrap(h.value()).getLong();
    }
}
