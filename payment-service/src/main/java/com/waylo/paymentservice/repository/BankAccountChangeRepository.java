package com.waylo.paymentservice.repository;

import com.waylo.paymentservice.domain.BankAccountChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BankAccountChangeRepository extends JpaRepository<BankAccountChange, UUID> {

    List<BankAccountChange> findByOrganizationIdOrderByOccurredAtDesc(UUID organizationId);

    @Override
    default void delete(BankAccountChange entity) { throw new UnsupportedOperationException("bank account changes are append-only"); }

    @Override
    default void deleteAll() { throw new UnsupportedOperationException("bank account changes are append-only"); }

    @Override
    default void deleteById(UUID id) { throw new UnsupportedOperationException("bank account changes are append-only"); }

    @Override
    default void deleteAll(Iterable<? extends BankAccountChange> entities) { throw new UnsupportedOperationException("bank account changes are append-only"); }

    @Override
    default void deleteAllById(Iterable<? extends UUID> ids) { throw new UnsupportedOperationException("bank account changes are append-only"); }

    @Override
    default void deleteAllInBatch() { throw new UnsupportedOperationException("bank account changes are append-only"); }

    @Override
    default void deleteAllInBatch(Iterable<BankAccountChange> entities) { throw new UnsupportedOperationException("bank account changes are append-only"); }

    @Override
    default void deleteAllByIdInBatch(Iterable<UUID> ids) { throw new UnsupportedOperationException("bank account changes are append-only"); }
}
