package com.waylo.paymentservice.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Per (organization, currency) lock row (PAYOUT-CONTRACT.md sec. 3.4). Callers lock the wallet first, then the payout row.
 * INSERT ... ON CONFLICT DO NOTHING followed by SELECT ... FOR UPDATE serialises every balance mutation of one wallet.
 */
@Repository
public class PayoutWalletRepository {

    private final EntityManager em;

    public PayoutWalletRepository(EntityManager em) {
        this.em = em;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(UUID organizationId, String currency) {
        em.createNativeQuery("INSERT INTO payout_wallets (organization_id, currency) VALUES (:o, :c) ON CONFLICT DO NOTHING")
                .setParameter("o", organizationId).setParameter("c", currency).executeUpdate();
        em.createNativeQuery("SELECT organization_id FROM payout_wallets WHERE organization_id = :o AND currency = :c FOR UPDATE")
                .setParameter("o", organizationId).setParameter("c", currency).getResultList();
    }
}
