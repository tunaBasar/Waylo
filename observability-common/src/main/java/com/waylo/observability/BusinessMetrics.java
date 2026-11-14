package com.waylo.observability;

import io.micrometer.core.instrument.Metrics;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Business counters on the global registry (Spring Boot binds it to the Prometheus registry). Inside a transaction the
 * increment waits for the commit, so a rolled back order/payment is never counted.
 */
public final class BusinessMetrics {

    private BusinessMetrics() {
    }

    public static void increment(String name, String... tags) {
        Runnable inc = () -> Metrics.counter(name, tags).increment();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    inc.run();
                }
            });
        } else {
            inc.run();
        }
    }
}
