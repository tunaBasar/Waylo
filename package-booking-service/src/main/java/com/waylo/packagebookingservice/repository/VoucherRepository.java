package com.waylo.packagebookingservice.repository;

import com.waylo.packagebookingservice.domain.Voucher;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoucherRepository extends JpaRepository<Voucher, UUID> {

    Optional<Voucher> findByOrderItemId(UUID orderItemId);

    List<Voucher> findByOrderItemIdIn(Collection<UUID> orderItemIds);

    /** ACTIVE vouchers whose window ended: the no-show job's work list. */
    List<Voucher> findTop200ByStatusAndValidUntilBeforeOrderByValidUntilAsc(com.waylo.packagebookingservice.domain.VoucherStatus status, java.time.Instant before);

    /** SELECT ... FOR UPDATE: two concurrent scans of one voucher serialize here. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where v.id = :id")
    Optional<Voucher> findByIdForUpdate(@Param("id") UUID id);
}
