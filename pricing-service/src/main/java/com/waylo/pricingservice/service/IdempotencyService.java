package com.waylo.pricingservice.service;

import com.waylo.pricingservice.domain.ProcessedEvent;
import com.waylo.pricingservice.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer idempotency kontrolü (INSERT-first yaklaşımı).
 *
 * Race condition'dan korunma: check-then-act yerine önce INSERT dener.
 * Composite PK (event_id, consumer_group) veritabanı seviyesinde atomik
 * benzersizlik garantisi sağlar. İkinci INSERT unique constraint violation
 * alır ve false döner, böylece iş mantığı hiç çalışmaz.
 *
 * consumer_group = spring.kafka.consumer.group-id. Aynı event farklı
 * servislerde bağımsız olarak işlenir.
 *
 * Kullanım:
 * <pre>
 * if (!idempotencyService.tryAcquire(event.getEventId())) {
 *     ack.acknowledge();
 *     return; // duplicate, iş mantığını çalıştırma
 * }
 * // iş mantığı...
 * ack.acknowledge();
 * </pre>
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final ProcessedEventRepository processedEventRepository;
    private final String consumerGroup;

    public IdempotencyService(ProcessedEventRepository processedEventRepository,
                              @Value("${spring.kafka.consumer.group-id}") String consumerGroup) {
        this.processedEventRepository = processedEventRepository;
        this.consumerGroup = consumerGroup;
    }

    /**
     * Event'i işlenmiş olarak kaydetmeyi dener.
     * INSERT başarılıysa true döner (ilk kez, iş mantığını çalıştır).
     * Unique constraint violation alırsa false döner (duplicate, atla).
     *
     * REQUIRES_NEW: ana transaction'dan bağımsız çalışır. Ana transaction
     * rollback olursa bu INSERT geri alınmaz ve event tekrar işlenmez.
     * Eğer iş mantığı başarısız olup event'in tekrar denenmesini istiyorsanız,
     * önce iş mantığını çalıştırıp sonra markProcessed() kullanın.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(String eventId) {
        try {
            processedEventRepository.save(new ProcessedEvent(eventId, consumerGroup));
            processedEventRepository.flush();
            log.debug("Event acquired: eventId={}, consumerGroup={}", eventId, consumerGroup);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("Event already processed (duplicate): eventId={}, consumerGroup={}",
                    eventId, consumerGroup);
            return false;
        }
    }

    /**
     * Event'i işlenmiş olarak kaydet (iş mantığı sonrası çağrılır).
     * tryAcquire() yerine bunu kullanırsanız, iş mantığı başarısız olduğunda
     * event tekrar denenebilir (at-least-once semantics korunur).
     */
    @Transactional
    public void markProcessed(String eventId) {
        if (!processedEventRepository.existsByEventIdAndConsumerGroup(eventId, consumerGroup)) {
            try {
                processedEventRepository.saveAndFlush(new ProcessedEvent(eventId, consumerGroup));
                log.debug("Event marked as processed: eventId={}, consumerGroup={}", eventId, consumerGroup);
            } catch (DataIntegrityViolationException e) {
                log.debug("Event concurrently marked as processed: eventId={}, consumerGroup={}",
                        eventId, consumerGroup);
            }
        }
    }

    /**
     * Event daha önce bu consumer group tarafından işlenmiş mi?
     */
    public boolean isProcessed(String eventId) {
        return processedEventRepository.existsByEventIdAndConsumerGroup(eventId, consumerGroup);
    }
}
