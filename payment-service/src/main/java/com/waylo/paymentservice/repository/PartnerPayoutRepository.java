package com.waylo.paymentservice.repository;

import com.waylo.paymentservice.domain.PartnerPayout;
import com.waylo.paymentservice.domain.PayoutStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerPayoutRepository extends JpaRepository<PartnerPayout, UUID>, JpaSpecificationExecutor<PartnerPayout> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PartnerPayout p where p.id = :id")
    Optional<PartnerPayout> findByIdForUpdate(@Param("id") UUID id);

    Optional<PartnerPayout> findByOrganizationIdAndIdempotencyKey(UUID organizationId, String idempotencyKey);

    Optional<PartnerPayout> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<PartnerPayout> findFirstByOrganizationIdAndCurrencyAndStatusIn(UUID organizationId, String currency, Collection<PayoutStatus> statuses);

    boolean existsByOrganizationIdAndStatusIn(UUID organizationId, Collection<PayoutStatus> statuses);

    boolean existsByPaymentReference(String paymentReference);

    /** Newest first. status may be null. Specification instead of "(:x is null or ...)" to avoid untyped null binds on PostgreSQL. */
    default Page<PartnerPayout> findForOrganization(UUID organizationId, PayoutStatus status, Pageable pageable) {
        Specification<PartnerPayout> spec = (root, q, cb) -> {
            q.orderBy(cb.desc(root.get("requestedAt")));
            return status == null ? cb.equal(root.get("organizationId"), organizationId)
                    : cb.and(cb.equal(root.get("organizationId"), organizationId), cb.equal(root.get("status"), status));
        };
        return findAll(spec, pageable);
    }

    /** Admin queue, oldest first. organizationId and currency may be null. */
    default Page<PartnerPayout> findQueue(Collection<PayoutStatus> statuses, UUID organizationId, String currency, Pageable pageable) {
        Specification<PartnerPayout> spec = (root, q, cb) -> {
            q.orderBy(cb.asc(root.get("requestedAt")));
            List<Predicate> ps = new ArrayList<>();
            ps.add(root.get("status").in(statuses));
            if (organizationId != null) ps.add(cb.equal(root.get("organizationId"), organizationId));
            if (currency != null) ps.add(cb.equal(root.get("currency"), currency));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return findAll(spec, pageable);
    }

    /** [currency, sum(amount)] of PAID payouts */
    @Query("select p.currency, coalesce(sum(p.amount), 0) from PartnerPayout p where p.organizationId = :orgId and p.status = com.waylo.paymentservice.domain.PayoutStatus.PAID group by p.currency")
    List<Object[]> paidTotalByCurrency(@Param("orgId") UUID organizationId);

    /** [currency, sum(amount)] of open payouts (REQUESTED, APPROVED, PROCESSING): money reserved and not yet paid */
    @Query("select p.currency, coalesce(sum(p.amount), 0) from PartnerPayout p where p.organizationId = :orgId and p.status in :open group by p.currency")
    List<Object[]> openAmountByCurrency(@Param("orgId") UUID organizationId, @Param("open") Collection<PayoutStatus> open);

    /** [organizationId, currency] without loading the entity, so the wallet lock is taken before the payout row is loaded. */
    @Query("select p.organizationId, p.currency from PartnerPayout p where p.id = :id")
    List<Object[]> findWalletKey(@Param("id") UUID id);
}
