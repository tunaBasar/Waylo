package com.waylo.packagebookingservice.repository;

import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderStatus;
import com.waylo.packagebookingservice.dto.PartnerOrderItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByFulfillmentRef(UUID fulfillmentRef);


    List<OrderItem> findByOrderId(UUID orderId);

    @Query("""
            select new com.waylo.packagebookingservice.dto.FlightAffectedItem(i.id, o.id, o.customerId)
            from OrderItem i join Order o on o.id = i.orderId
            where i.vertical = com.waylo.packagebookingservice.domain.OrderItemVertical.FLIGHT
              and i.flightNumber = :flightNumber
              and i.departureAt >= :fromTs
              and i.departureAt < :toTs
              and o.status = com.waylo.packagebookingservice.domain.OrderStatus.CONFIRMED
              and i.cancelOutcome is null
            """)
    List<com.waylo.packagebookingservice.dto.FlightAffectedItem> findConfirmedFlightItems(
            @Param("flightNumber") String flightNumber, @Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs);

    List<OrderItem> findByOrderIdIn(java.util.Collection<UUID> orderIds);

    @Query(value = """
            select new com.waylo.packagebookingservice.dto.PartnerOrderItemResponse(
                i.id, o.id, cast(o.status as string), o.createdAt, i.description,
                i.unitAmount * i.quantity, i.currency, i.fulfillmentRef, i.pickupAt, i.returnAt)
            from OrderItem i join Order o on o.id = i.orderId
            where i.supplierOrganizationId = :orgId
              and o.status in :statuses
              and o.createdAt >= :fromTs and o.createdAt < :toTs
            order by o.createdAt desc, i.id
            """,
            countQuery = """
            select count(i) from OrderItem i join Order o on o.id = i.orderId
            where i.supplierOrganizationId = :orgId
              and o.status in :statuses
              and o.createdAt >= :fromTs and o.createdAt < :toTs
            """)
    Page<PartnerOrderItemResponse> findPartnerItems(@Param("orgId") UUID orgId,
                                                    @Param("statuses") java.util.Collection<OrderStatus> statuses,
                                                    @Param("fromTs") Instant fromTs,
                                                    @Param("toTs") Instant toTs,
                                                    Pageable pageable);
}
