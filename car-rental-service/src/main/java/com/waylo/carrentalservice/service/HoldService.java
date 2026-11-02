package com.waylo.carrentalservice.service;

import com.waylo.carrentalservice.client.AuthGateway;
import com.waylo.carrentalservice.client.OrgInfo;
import com.waylo.carrentalservice.config.CarSettings;
import com.waylo.carrentalservice.domain.*;
import com.waylo.carrentalservice.dto.CarDtos.HoldDto;
import com.waylo.carrentalservice.dto.CarDtos.HoldRequest;
import com.waylo.carrentalservice.dto.CarDtos.RentalResponse;
import com.waylo.carrentalservice.exception.BusinessRuleException;
import com.waylo.carrentalservice.exception.ConflictException;
import com.waylo.carrentalservice.exception.ResourceNotFoundException;
import com.waylo.carrentalservice.repository.*;
import com.waylo.carrentalservice.rules.Availability;
import com.waylo.carrentalservice.rules.RentalRules;
import com.waylo.carrentalservice.rules.Sellability;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Internal hold lifecycle used by package-booking-service. */
@Service
public class HoldService {

    private final AuthGateway auth;
    private final CarSettings settings;
    private final Clock clock;
    private final BranchRepository branches;
    private final VehicleRepository vehicles;
    private final CategoryRateRepository rates;
    private final OneWayRouteRepository routes;
    private final CarDocumentRepository documents;
    private final RentalRepository rentals;

    public HoldService(AuthGateway auth, CarSettings settings, Clock clock, BranchRepository branches,
                       VehicleRepository vehicles, CategoryRateRepository rates, OneWayRouteRepository routes,
                       CarDocumentRepository documents, RentalRepository rentals) {
        this.auth = auth;
        this.settings = settings;
        this.clock = clock;
        this.branches = branches;
        this.vehicles = vehicles;
        this.rates = rates;
        this.routes = routes;
        this.documents = documents;
        this.rentals = rentals;
    }

    @Transactional
    public HoldDto hold(HoldRequest req) {
        Optional<Rental> existing = rentals.findByIdempotencyKey(req.idempotencyKey());
        if (existing.isPresent()) return toDto(existing.get());

        Branch pickup = branches.findById(req.pickupBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + req.pickupBranchId()));
        Branch ret = branches.findById(req.returnBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + req.returnBranchId()));
        Instant now = clock.instant();
        Instant pickupAt = RentalRules.toInstant(req.pickupAt());
        Instant returnAt = RentalRules.toInstant(req.returnAt());
        int days = RentalRules.validateTimes(now, req.pickupAt(), req.returnAt(), pickupAt, returnAt,
                pickup.openTime, pickup.closeTime, ret.openTime, ret.closeTime);
        if (!pickup.active || !ret.active) {
            throw new BusinessRuleException("PARTNER_NOT_SELLABLE", "Branch is not active");
        }
        if (!pickup.organizationId.equals(ret.organizationId)) {
            throw new BusinessRuleException("ROUTE_NOT_OFFERED", "Return branch belongs to another partner");
        }
        LocalDate returnDate = req.returnAt().toLocalDate();
        OrgInfo org = auth.lookupOrEmpty(pickup.organizationId)
                .filter(o -> o.active() && o.carRental())
                .orElseThrow(() -> new BusinessRuleException("PARTNER_NOT_SELLABLE", "Partner is not sellable"));
        if (!Sellability.individualGateOpen(org, settings.individualPartnersEnabled())) {
            throw new BusinessRuleException("INDIVIDUAL_PARTNERS_DISABLED", "Individual partners are not enabled");
        }
        if (!Sellability.missingOrgDocuments(org, documents.findByOrganizationIdAndVehicleIdIsNull(org.id()), returnDate).isEmpty()) {
            throw new BusinessRuleException("PARTNER_NOT_SELLABLE", "Partner documents are missing or expired");
        }
        BigDecimal oneWayFee = BigDecimal.ZERO.setScale(2);
        String routeCurrency = null;
        if (!pickup.id.equals(ret.id)) {
            OneWayRoute route = routes.findByFromBranchIdAndToBranchId(pickup.id, ret.id)
                    .orElseThrow(() -> new BusinessRuleException("ROUTE_NOT_OFFERED", "One-way return is not offered"));
            oneWayFee = route.fee;
            routeCurrency = route.currency;
        }
        CategoryRate rate = rates.findByBranchIdAndCategoryAndTransmission(pickup.id, req.category(), req.transmission())
                .orElseThrow(() -> new BusinessRuleException("NO_RATE", "No rate for this category at the pickup branch"));
        if (req.driverAge() < rate.minDriverAge) {
            throw new BusinessRuleException("DRIVER_TOO_YOUNG", "Minimum driver age is " + rate.minDriverAge);
        }
        if (routeCurrency != null && !routeCurrency.equals(rate.currency)) {
            throw new BusinessRuleException("ROUTE_NOT_OFFERED", "One-way route currency does not match the rate");
        }

        List<Vehicle> candidates = vehicles.lockCandidates(org.id(), req.category(), req.transmission());
        // re-check the key now that we hold the row locks: a concurrent same-key hold has committed by now
        existing = rentals.findByIdempotencyKey(req.idempotencyKey());
        if (existing.isPresent()) return toDto(existing.get());

        List<UUID> ids = candidates.stream().map(v -> v.id).toList();
        Map<UUID, List<Rental>> blocking = rentals.findBlocking(ids, now).stream()
                .collect(Collectors.groupingBy(r -> r.vehicleId));
        Map<UUID, List<CarDocument>> docs = documents.findByVehicleIdIn(ids).stream()
                .collect(Collectors.groupingBy(d -> d.vehicleId));
        Vehicle chosen = null;
        for (Vehicle v : candidates) {
            if (!Sellability.missingVehicleDocuments(docs.getOrDefault(v.id, List.of()), returnDate).isEmpty()) continue;
            if (Availability.isFree(v, blocking.getOrDefault(v.id, List.of()), pickupAt, returnAt, pickup.id, ret.id)) {
                chosen = v;
                break;
            }
        }
        if (chosen == null) {
            throw new ConflictException("NO_VEHICLE_AVAILABLE", "No vehicle is available for the requested period");
        }

        Rental r = new Rental();
        r.id = UUID.randomUUID();
        r.organizationId = org.id();
        r.customerId = req.principalUserId();
        r.vehicleId = chosen.id;
        r.pickupBranchId = pickup.id;
        r.returnBranchId = ret.id;
        r.category = req.category();
        r.transmission = req.transmission();
        r.pickupAt = pickupAt;
        r.returnAt = returnAt;
        r.chargeableDays = days;
        r.dailyRate = rate.dailyRate;
        r.oneWayFee = oneWayFee;
        r.totalAmount = RentalRules.total(days, rate.dailyRate, oneWayFee);
        r.currency = rate.currency;
        r.depositAmount = rate.depositAmount;
        r.kmLimitPerDay = rate.kmLimitPerDay;
        r.driverAge = req.driverAge();
        r.status = RentalStatus.HELD;
        r.holdExpiresAt = now.plusSeconds(settings.holdTtlMinutes() * 60L);
        r.idempotencyKey = req.idempotencyKey();
        r.orderCorrelationId = req.orderCorrelationId();
        return toDto(rentals.saveAndFlush(r), chosen);
    }

    @Transactional
    public HoldDto confirm(UUID id) {
        Rental r = rentals.findById(id).orElseThrow(() -> new ResourceNotFoundException("Hold not found: " + id));
        if (r.status == RentalStatus.CONFIRMED) return toDto(r);
        if (r.status != RentalStatus.HELD || !r.holdExpiresAt.isAfter(clock.instant())) {
            throw new ConflictException("HOLD_EXPIRED", "The hold has expired or was released");
        }
        r.status = RentalStatus.CONFIRMED;
        return toDto(rentals.save(r));
    }

    @Transactional
    public HoldDto release(UUID id) {
        Rental r = rentals.findById(id).orElseThrow(() -> new ResourceNotFoundException("Hold not found: " + id));
        if (r.status == RentalStatus.HELD || r.status == RentalStatus.CONFIRMED) {
            r.status = RentalStatus.CANCELLED;
            rentals.save(r);
        }
        return toDto(r);
    }

    /** Marks HELD rentals whose TTL passed as EXPIRED. Returns the number expired. */
    @Transactional
    public int expireOverdue() {
        List<Rental> overdue = rentals.findExpiredHolds(clock.instant());
        overdue.forEach(r -> r.status = RentalStatus.EXPIRED);
        rentals.saveAll(overdue);
        return overdue.size();
    }

    @Transactional(readOnly = true)
    public List<RentalResponse> mine(UUID userId) {
        return rentals.findByCustomerIdOrderByCreatedAtDesc(userId).stream().map(RentalResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public RentalResponse get(UUID id, UUID userId, boolean admin) {
        Rental r = rentals.findById(id).orElseThrow(() -> new ResourceNotFoundException("Rental not found: " + id));
        if (!admin && !r.customerId.equals(userId)) {
            throw new ResourceNotFoundException("Rental not found: " + id);
        }
        return RentalResponse.of(r);
    }

    private HoldDto toDto(Rental r) {
        return toDto(r, vehicles.findById(r.vehicleId).orElseThrow());
    }

    private HoldDto toDto(Rental r, Vehicle v) {
        return new HoldDto(r.id, r.organizationId, r.pickupBranchId, r.returnBranchId, r.category, r.transmission,
                v.exampleModel(), com.waylo.carrentalservice.dto.CarDtos.local(r.pickupAt),
                com.waylo.carrentalservice.dto.CarDtos.local(r.returnAt), r.chargeableDays, r.currency, r.totalAmount,
                r.depositAmount, r.kmLimitPerDay, r.status, r.holdExpiresAt);
    }
}
