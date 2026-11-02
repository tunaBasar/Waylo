package com.waylo.carrentalservice.service;

import com.waylo.carrentalservice.client.AuthGateway;
import com.waylo.carrentalservice.client.OrgInfo;
import com.waylo.carrentalservice.config.CarSettings;
import com.waylo.carrentalservice.domain.*;
import com.waylo.carrentalservice.dto.CarDtos.OfferResponse;
import com.waylo.carrentalservice.dto.CarDtos.PublicBranchResponse;
import com.waylo.carrentalservice.exception.ResourceNotFoundException;
import com.waylo.carrentalservice.repository.*;
import com.waylo.carrentalservice.rules.Availability;
import com.waylo.carrentalservice.rules.RentalRules;
import com.waylo.carrentalservice.rules.Sellability;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Public catalogue: sellable branches and offers, computed at query time. */
@Service
public class OfferService {

    private final AuthGateway auth;
    private final CarSettings settings;
    private final Clock clock;
    private final BranchRepository branches;
    private final VehicleRepository vehicles;
    private final CategoryRateRepository rates;
    private final OneWayRouteRepository routes;
    private final CarDocumentRepository documents;
    private final RentalRepository rentals;

    public OfferService(AuthGateway auth, CarSettings settings, Clock clock, BranchRepository branches,
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

    @Transactional(readOnly = true)
    public List<PublicBranchResponse> sellableBranches(String city) {
        LocalDate today = LocalDate.now(clock.withZone(CarSettings.ZONE));
        Map<UUID, Boolean> orgOk = new HashMap<>();
        return branches.findActive(city == null || city.isBlank() ? null : city.trim()).stream()
                .filter(b -> orgOk.computeIfAbsent(b.organizationId, id -> orgSellable(id, today)))
                .map(PublicBranchResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> offers(UUID pickupBranchId, UUID returnBranchId, LocalDateTime pickupLocal,
                                      LocalDateTime returnLocal, int driverAge) {
        Branch pickup = branches.findById(pickupBranchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + pickupBranchId));
        Branch ret = branches.findById(returnBranchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + returnBranchId));
        Instant pickupAt = RentalRules.toInstant(pickupLocal);
        Instant returnAt = RentalRules.toInstant(returnLocal);
        int days = RentalRules.validateTimes(clock.instant(), pickupLocal, returnLocal, pickupAt, returnAt,
                pickup.openTime, pickup.closeTime, ret.openTime, ret.closeTime);
        if (!pickup.active || !ret.active || !pickup.organizationId.equals(ret.organizationId)) return List.of();
        LocalDate returnDate = returnLocal.toLocalDate();
        Optional<OrgInfo> org = auth.lookupOrEmpty(pickup.organizationId);
        if (org.isEmpty() || !Sellability.orgSellable(org.get(),
                documents.findByOrganizationIdAndVehicleIdIsNull(pickup.organizationId), returnDate,
                settings.individualPartnersEnabled())) return List.of();

        java.math.BigDecimal oneWayFee = java.math.BigDecimal.ZERO.setScale(2);
        String routeCurrency = null;
        if (!pickup.id.equals(ret.id)) {
            Optional<OneWayRoute> route = routes.findByFromBranchIdAndToBranchId(pickup.id, ret.id);
            if (route.isEmpty()) return List.of();
            oneWayFee = route.get().fee;
            routeCurrency = route.get().currency;
        }
        String authNumber = org.get().individual() ? null
                : Sellability.effective(documents.findByOrganizationIdAndVehicleIdIsNull(pickup.organizationId),
                        DocumentType.RENTAL_AUTHORIZATION).map(d -> d.documentNumber).orElse(null);

        List<Vehicle> fleet = vehicles.findByOrganizationIdAndActiveTrueOrderById(pickup.organizationId);
        Map<UUID, List<CarDocument>> docs = documents.findByVehicleIdIn(fleet.stream().map(v -> v.id).toList())
                .stream().collect(Collectors.groupingBy(d -> d.vehicleId));
        Map<UUID, List<Rental>> blocking = rentals.findBlocking(fleet.stream().map(v -> v.id).toList(), clock.instant())
                .stream().collect(Collectors.groupingBy(r -> r.vehicleId));

        Map<String, Vehicle> firstFree = new LinkedHashMap<>();
        for (Vehicle v : fleet) {
            if (!Sellability.missingVehicleDocuments(docs.getOrDefault(v.id, List.of()), returnDate).isEmpty()) continue;
            if (!Availability.isFree(v, blocking.getOrDefault(v.id, List.of()), pickupAt, returnAt, pickup.id, ret.id)) continue;
            firstFree.putIfAbsent(v.category + "/" + v.transmission, v);
        }
        List<OfferResponse> result = new ArrayList<>();
        for (Vehicle v : firstFree.values()) {
            Optional<CategoryRate> rate = rates.findByBranchIdAndCategoryAndTransmission(pickup.id, v.category, v.transmission);
            if (rate.isEmpty() || driverAge < rate.get().minDriverAge) continue;
            CategoryRate r = rate.get();
            if (routeCurrency != null && !routeCurrency.equals(r.currency)) continue;
            result.add(new OfferResponse(pickup.id, ret.id, pickup.organizationId, v.category, v.transmission,
                    v.exampleModel(), v.seatCount, v.fuelType, days, r.dailyRate, oneWayFee,
                    RentalRules.total(days, r.dailyRate, oneWayFee), r.currency, r.depositAmount, r.kmLimitPerDay,
                    r.extraKmFee, r.minDriverAge, r.minLicenseYears, authNumber));
        }
        return result;
    }

    private boolean orgSellable(UUID orgId, LocalDate on) {
        return auth.lookupOrEmpty(orgId).filter(o -> Sellability.orgSellable(o,
                documents.findByOrganizationIdAndVehicleIdIsNull(orgId), on, settings.individualPartnersEnabled())).isPresent();
    }
}
