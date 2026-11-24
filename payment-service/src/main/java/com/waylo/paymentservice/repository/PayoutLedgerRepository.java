package com.waylo.paymentservice.repository;

import com.waylo.paymentservice.domain.PayoutLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Append-only: deletes are blocked here and by the V9 trigger. */
public interface PayoutLedgerRepository extends JpaRepository<PayoutLedgerEntry, UUID> {

    /** [currency, sum(payable_delta)] */
    @Query("select l.currency, coalesce(sum(l.payableDelta), 0) from PayoutLedgerEntry l where l.organizationId = :orgId group by l.currency")
    List<Object[]> deltaByCurrency(@Param("orgId") UUID orgId);

    /** [currency, sum(payable_delta)] of adjustments only */
    @Query("select l.currency, coalesce(sum(l.payableDelta), 0) from PayoutLedgerEntry l where l.organizationId = :orgId " +
            "and l.entryType in (com.waylo.paymentservice.domain.PayoutLedgerType.ADJUSTMENT_DEBIT, com.waylo.paymentservice.domain.PayoutLedgerType.ADJUSTMENT_CREDIT) group by l.currency")
    List<Object[]> adjustmentsByCurrency(@Param("orgId") UUID orgId);

    Optional<PayoutLedgerEntry> findFirstByOrganizationIdAndIdempotencyKey(UUID organizationId, String idempotencyKey);

    List<PayoutLedgerEntry> findByPayoutIdOrderByCreatedAtAscIdAsc(UUID payoutId);

    @Override
    default void delete(PayoutLedgerEntry entity) { throw new UnsupportedOperationException("payout ledger is append-only"); }

    @Override
    default void deleteAll() { throw new UnsupportedOperationException("payout ledger is append-only"); }

    @Override
    default void deleteById(UUID id) { throw new UnsupportedOperationException("payout ledger is append-only"); }

    @Override
    default void deleteAll(Iterable<? extends PayoutLedgerEntry> entities) { throw new UnsupportedOperationException("payout ledger is append-only"); }

    @Override
    default void deleteAllById(Iterable<? extends UUID> ids) { throw new UnsupportedOperationException("payout ledger is append-only"); }

    @Override
    default void deleteAllInBatch() { throw new UnsupportedOperationException("payout ledger is append-only"); }

    @Override
    default void deleteAllInBatch(Iterable<PayoutLedgerEntry> entities) { throw new UnsupportedOperationException("payout ledger is append-only"); }

    @Override
    default void deleteAllByIdInBatch(Iterable<UUID> ids) { throw new UnsupportedOperationException("payout ledger is append-only"); }
}
