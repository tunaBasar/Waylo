package com.waylo.paymentservice.repository;

import com.waylo.paymentservice.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Only reads and inserts are exposed. Update/delete are additionally blocked by a database trigger. */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByOrderIdOrderByOccurredAtAscEntryIdAsc(UUID orderId);

    @Query("select coalesce(sum(e.debitAmount) - sum(e.creditAmount), 0) from LedgerEntry e where e.orderId = :orderId")
    BigDecimal balanceOfOrder(@Param("orderId") UUID orderId);

    @Override
    default void delete(LedgerEntry entity) { throw new UnsupportedOperationException("ledger is append-only"); }

    @Override
    default void deleteAll() { throw new UnsupportedOperationException("ledger is append-only"); }

    @Override
    default void deleteById(UUID id) { throw new UnsupportedOperationException("ledger is append-only"); }

    @Override
    default void deleteAll(Iterable<? extends LedgerEntry> entities) { throw new UnsupportedOperationException("ledger is append-only"); }

    @Override
    default void deleteAllById(Iterable<? extends UUID> ids) { throw new UnsupportedOperationException("ledger is append-only"); }

    @Override
    default void deleteAllInBatch() { throw new UnsupportedOperationException("ledger is append-only"); }

    @Override
    default void deleteAllInBatch(Iterable<LedgerEntry> entities) { throw new UnsupportedOperationException("ledger is append-only"); }

    @Override
    default void deleteAllByIdInBatch(Iterable<UUID> ids) { throw new UnsupportedOperationException("ledger is append-only"); }
}
