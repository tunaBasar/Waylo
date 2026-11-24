package com.waylo.paymentservice.repository;

import com.waylo.paymentservice.domain.EscrowItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EscrowItemRepository extends JpaRepository<EscrowItem, UUID> {

    Optional<EscrowItem> findByOrderItemId(UUID orderItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EscrowItem e where e.orderItemId = :orderItemId")
    Optional<EscrowItem> findByOrderItemIdForUpdate(@Param("orderItemId") UUID orderItemId);

    List<EscrowItem> findByPaymentIntentId(UUID paymentIntentId);

    List<EscrowItem> findByOrderId(UUID orderId);

    @Query("select e from EscrowItem e where e.supplierOrganizationId = :orgId and e.heldAt >= :fromTs and e.heldAt < :toTs order by e.heldAt desc")
    List<EscrowItem> findForSupplier(@Param("orgId") UUID orgId, @Param("fromTs") java.time.Instant fromTs,
                                     @Param("toTs") java.time.Instant toTs);

    /** [currency, sum(supplier_amount)] RELEASED and past the holdback cutoff. supplier_amount is already net of commission. */
    @Query("select e.currency, coalesce(sum(e.supplierAmount), 0) from EscrowItem e " +
            "where e.supplierOrganizationId = :orgId and e.status = com.waylo.paymentservice.domain.EscrowStatus.RELEASED " +
            "and e.releasedAt <= :cutoff group by e.currency")
    List<Object[]> eligibleReleasedByCurrency(@Param("orgId") UUID orgId, @Param("cutoff") java.time.Instant cutoff);

    @Query("select e.currency, coalesce(sum(e.supplierAmount), 0) from EscrowItem e " +
            "where e.supplierOrganizationId = :orgId and e.status = com.waylo.paymentservice.domain.EscrowStatus.RELEASED " +
            "and e.releasedAt > :cutoff group by e.currency")
    List<Object[]> releasedInHoldbackByCurrency(@Param("orgId") UUID orgId, @Param("cutoff") java.time.Instant cutoff);

    @Query("select e.currency, coalesce(sum(e.supplierAmount), 0) from EscrowItem e " +
            "where e.supplierOrganizationId = :orgId and e.status = com.waylo.paymentservice.domain.EscrowStatus.HELD group by e.currency")
    List<Object[]> heldByCurrency(@Param("orgId") UUID orgId);

    /** [currency, sum(credit_amount)] of SUPPLIER_PAYABLE ledger rows of this organization's items (reconciliation, ledger has no org column). */
    @Query(value = "select l.currency, coalesce(sum(l.credit_amount), 0) from ledger_entries l " +
            "join escrow_items e on e.order_item_id = l.order_item_id " +
            "where e.supplier_organization_id = :orgId and l.account = 'SUPPLIER_PAYABLE' group by l.currency", nativeQuery = true)
    List<Object[]> ledgerSupplierPayableByCurrency(@Param("orgId") UUID orgId);
}
