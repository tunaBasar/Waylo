package com.waylo.paymentservice.repository;

import com.waylo.paymentservice.domain.PayoutEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PayoutEventRepository extends JpaRepository<PayoutEvent, UUID> {

    List<PayoutEvent> findByPayoutIdOrderByOccurredAtAscIdAsc(UUID payoutId);

    @Override
    default void delete(PayoutEvent entity) { throw new UnsupportedOperationException("payout events are append-only"); }

    @Override
    default void deleteAll() { throw new UnsupportedOperationException("payout events are append-only"); }

    @Override
    default void deleteById(UUID id) { throw new UnsupportedOperationException("payout events are append-only"); }

    @Override
    default void deleteAll(Iterable<? extends PayoutEvent> entities) { throw new UnsupportedOperationException("payout events are append-only"); }

    @Override
    default void deleteAllById(Iterable<? extends UUID> ids) { throw new UnsupportedOperationException("payout events are append-only"); }

    @Override
    default void deleteAllInBatch() { throw new UnsupportedOperationException("payout events are append-only"); }

    @Override
    default void deleteAllInBatch(Iterable<PayoutEvent> entities) { throw new UnsupportedOperationException("payout events are append-only"); }

    @Override
    default void deleteAllByIdInBatch(Iterable<UUID> ids) { throw new UnsupportedOperationException("payout events are append-only"); }
}
