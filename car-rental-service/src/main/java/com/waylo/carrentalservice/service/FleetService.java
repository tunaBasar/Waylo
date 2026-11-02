package com.waylo.carrentalservice.service;

import com.waylo.carrentalservice.client.AuthGateway;
import com.waylo.carrentalservice.client.OrgInfo;
import com.waylo.carrentalservice.config.CarSettings;
import com.waylo.carrentalservice.domain.*;
import com.waylo.carrentalservice.dto.CarDtos.*;
import com.waylo.carrentalservice.exception.BusinessRuleException;
import com.waylo.carrentalservice.exception.DuplicateResourceException;
import com.waylo.carrentalservice.exception.ResourceNotFoundException;
import com.waylo.carrentalservice.repository.*;
import com.waylo.carrentalservice.rules.Sellability;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Partner fleet management: branches, vehicles, rates, one-way routes and status. */
@Service
public class FleetService {

    private final AuthGateway auth;
    private final CarSettings settings;
    private final Clock clock;
    private final BranchRepository branches;
    private final VehicleRepository vehicles;
    private final CategoryRateRepository rates;
    private final OneWayRouteRepository routes;
    private final CarDocumentRepository documents;
    private final RentalRepository rentals;

    public FleetService(AuthGateway auth, CarSettings settings, Clock clock, BranchRepository branches,
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

    private LocalDate today() { return LocalDate.now(clock.withZone(CarSettings.ZONE)); }

    // ---- branches ----

    @Transactional(readOnly = true)
    public List<BranchResponse> listBranches(UUID userId, UUID orgId) {
        auth.requireFleetManager(userId, orgId);
        return branches.findByOrganizationIdOrderByName(orgId).stream().map(BranchResponse::of).toList();
    }

    @Transactional
    public BranchResponse createBranch(UUID userId, UUID orgId, BranchRequest req) {
        auth.requireFleetManager(userId, orgId);
        Branch b = new Branch();
        b.id = UUID.randomUUID();
        b.organizationId = orgId;
        applyBranch(b, req);
        return BranchResponse.of(branches.save(b));
    }

    @Transactional
    public BranchResponse updateBranch(UUID userId, UUID orgId, UUID id, BranchRequest req) {
        auth.requireFleetManager(userId, orgId);
        Branch b = ownBranch(orgId, id);
        applyBranch(b, req);
        return BranchResponse.of(branches.save(b));
    }

    private void applyBranch(Branch b, BranchRequest req) {
        if (!req.closeTime().isAfter(req.openTime())) {
            throw new BusinessRuleException("INVALID_HOURS", "closeTime must be after openTime");
        }
        b.name = req.name().trim();
        b.city = req.city().trim();
        b.address = req.address().trim();
        b.phone = req.phone() == null || req.phone().isBlank() ? null : req.phone().trim();
        b.openTime = req.openTime();
        b.closeTime = req.closeTime();
        if (req.active() != null) b.active = req.active();
    }

    private Branch ownBranch(UUID orgId, UUID id) {
        return branches.findById(id).filter(b -> b.organizationId.equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + id));
    }

    // ---- vehicles ----

    @Transactional(readOnly = true)
    public List<VehicleResponse> listVehicles(UUID userId, UUID orgId) {
        OrgInfo org = auth.requireFleetManager(userId, orgId);
        List<CarDocument> orgDocs = documents.findByOrganizationIdAndVehicleIdIsNull(orgId);
        boolean orgOk = Sellability.orgSellable(org, orgDocs, today(), settings.individualPartnersEnabled());
        List<Vehicle> list = vehicles.findByOrganizationIdOrderByPlateNumber(orgId);
        Map<UUID, List<CarDocument>> docsByVehicle = documents.findByVehicleIdIn(
                list.stream().map(v -> v.id).toList()).stream().collect(Collectors.groupingBy(d -> d.vehicleId));
        return list.stream().map(v -> toResponse(v, orgOk, docsByVehicle.getOrDefault(v.id, List.of()))).toList();
    }

    @Transactional
    public VehicleResponse createVehicle(UUID userId, UUID orgId, VehicleRequest req) {
        OrgInfo org = auth.requireFleetManager(userId, orgId);
        Vehicle v = new Vehicle();
        v.id = UUID.randomUUID();
        v.organizationId = orgId;
        applyVehicle(v, orgId, req);
        return saveVehicle(org, v);
    }

    @Transactional
    public VehicleResponse updateVehicle(UUID userId, UUID orgId, UUID id, VehicleRequest req) {
        OrgInfo org = auth.requireFleetManager(userId, orgId);
        Vehicle v = ownVehicle(orgId, id);
        applyVehicle(v, orgId, req);
        return saveVehicle(org, v);
    }

    @Transactional
    public VehicleResponse setActive(UUID userId, UUID orgId, UUID id, boolean active) {
        OrgInfo org = auth.requireFleetManager(userId, orgId);
        Vehicle v = ownVehicle(orgId, id);
        v.active = active;
        return saveVehicle(org, v);
    }

    private VehicleResponse saveVehicle(OrgInfo org, Vehicle v) {
        Vehicle saved = vehicles.save(v);
        boolean orgOk = Sellability.orgSellable(org,
                documents.findByOrganizationIdAndVehicleIdIsNull(org.id()), today(), settings.individualPartnersEnabled());
        return toResponse(saved, orgOk, documents.findByVehicleId(saved.id));
    }

    private VehicleResponse toResponse(Vehicle v, boolean orgOk, List<CarDocument> docs) {
        List<DocumentType> missing = Sellability.missingVehicleDocuments(docs, today());
        return VehicleResponse.of(v, v.active && orgOk && missing.isEmpty(), missing);
    }

    private void applyVehicle(Vehicle v, UUID orgId, VehicleRequest req) {
        ownBranch(orgId, req.homeBranchId());
        String plate = req.plateNumber().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (plate.isEmpty()) {
            throw new BusinessRuleException("INVALID_PLATE", "plateNumber must not be blank");
        }
        vehicles.findByPlateNumber(plate).filter(o -> !o.id.equals(v.id)).ifPresent(o -> {
            throw new DuplicateResourceException("Plate number already registered: " + plate);
        });
        v.homeBranchId = req.homeBranchId();
        v.plateNumber = plate;
        v.brand = req.brand().trim();
        v.model = req.model().trim();
        v.year = req.year();
        v.category = req.category();
        v.transmission = req.transmission();
        v.fuelType = req.fuelType();
        v.seatCount = req.seatCount();
        if (req.active() != null) v.active = req.active();
    }

    private Vehicle ownVehicle(UUID orgId, UUID id) {
        return vehicles.findById(id).filter(v -> v.organizationId.equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
    }

    // ---- rates ----

    @Transactional(readOnly = true)
    public List<RateResponse> listRates(UUID userId, UUID orgId) {
        auth.requireFleetManager(userId, orgId);
        return rates.findByOrganizationId(orgId).stream().map(RateResponse::of).toList();
    }

    @Transactional
    public List<RateResponse> upsertRates(UUID userId, UUID orgId, RatesRequest req) {
        auth.requireFleetManager(userId, orgId);
        for (RateItem item : req.items()) {
            ownBranch(orgId, item.branchId());
            CategoryRate r = rates.findByBranchIdAndCategoryAndTransmission(item.branchId(), item.category(), item.transmission())
                    .orElseGet(() -> {
                        CategoryRate n = new CategoryRate();
                        n.id = UUID.randomUUID();
                        n.organizationId = orgId;
                        n.branchId = item.branchId();
                        n.category = item.category();
                        n.transmission = item.transmission();
                        return n;
                    });
            r.currency = item.currency();
            r.dailyRate = item.dailyRate();
            r.depositAmount = item.depositAmount();
            r.kmLimitPerDay = item.kmLimitPerDay();
            r.extraKmFee = item.extraKmFee();
            r.minDriverAge = item.minDriverAge();
            r.minLicenseYears = item.minLicenseYears();
            rates.save(r);
        }
        return rates.findByOrganizationId(orgId).stream().map(RateResponse::of).toList();
    }

    // ---- one-way routes ----

    @Transactional(readOnly = true)
    public List<RouteResponse> listRoutes(UUID userId, UUID orgId) {
        auth.requireFleetManager(userId, orgId);
        return routes.findByOrganizationId(orgId).stream().map(RouteResponse::of).toList();
    }

    @Transactional
    public List<RouteResponse> replaceRoutes(UUID userId, UUID orgId, RoutesRequest req) {
        auth.requireFleetManager(userId, orgId);
        Set<String> seen = new HashSet<>();
        List<OneWayRoute> fresh = new ArrayList<>();
        for (RouteItem item : req.routes()) {
            if (item.fromBranchId().equals(item.toBranchId())) {
                throw new BusinessRuleException("INVALID_ROUTE", "from and to branch must differ");
            }
            if (!seen.add(item.fromBranchId() + ">" + item.toBranchId())) {
                throw new BusinessRuleException("INVALID_ROUTE", "Duplicate route");
            }
            ownBranch(orgId, item.fromBranchId());
            ownBranch(orgId, item.toBranchId());
            // currency of route fee must equal the rate currency of the origin branch
            for (CategoryRate rate : rates.findByBranchId(item.fromBranchId())) {
                if (!rate.currency.equals(item.currency())) {
                    throw new BusinessRuleException("CURRENCY_MISMATCH",
                            "Route currency must equal the rate currency of the origin branch");
                }
            }
            OneWayRoute r = new OneWayRoute();
            r.id = UUID.randomUUID();
            r.organizationId = orgId;
            r.fromBranchId = item.fromBranchId();
            r.toBranchId = item.toBranchId();
            r.fee = item.fee();
            r.currency = item.currency();
            fresh.add(r);
        }
        routes.deleteByOrganizationId(orgId);
        routes.flush();
        return routes.saveAll(fresh).stream().map(RouteResponse::of).toList();
    }

    // ---- rentals / status / admin ----

    @Transactional(readOnly = true)
    public List<RentalResponse> partnerRentals(UUID userId, UUID orgId) {
        auth.requireFleetManager(userId, orgId);
        return rentals.findByOrganizationIdAndStatusInOrderByCreatedAtDesc(orgId,
                List.of(RentalStatus.CONFIRMED, RentalStatus.CANCELLED)).stream().map(RentalResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public PartnerStatus status(UUID userId, UUID orgId) {
        OrgInfo org = auth.requireFleetManager(userId, orgId);
        List<DocumentType> missing = Sellability.missingOrgDocuments(org,
                documents.findByOrganizationIdAndVehicleIdIsNull(orgId), today());
        boolean gate = Sellability.individualGateOpen(org, settings.individualPartnersEnabled());
        return new PartnerStatus(org.active() && gate && missing.isEmpty(), missing, gate);
    }

    @Transactional(readOnly = true)
    public List<AdminVehicleResponse> adminVehicles() {
        LocalDate today = today();
        List<Vehicle> list = vehicles.findAllByOrderByOrganizationIdAscPlateNumberAsc();

        // Batch collect org IDs and branch IDs
        Set<UUID> orgIds = list.stream().map(v -> v.organizationId).collect(Collectors.toSet());
        Set<UUID> branchIds = list.stream().map(v -> v.homeBranchId).collect(Collectors.toSet());

        // Cache org lookups per request
        Map<UUID, OrgInfo> orgCache = new HashMap<>();
        for (UUID orgId : orgIds) {
            try {
                orgCache.put(orgId, auth.organization(orgId));
            } catch (Exception e) { /* null on fail */ }
        }

        // Cache branch names
        List<Branch> branchList = branches.findByIdIn(branchIds);
        Map<UUID, String> branchNames = new HashMap<>();
        for (Branch b : branchList) {
            branchNames.put(b.id, b.name);
        }

        // Cache org-level docs
        Map<UUID, List<CarDocument>> orgDocs = new HashMap<>();
        for (UUID orgId : orgIds) {
            orgDocs.put(orgId, documents.findByOrganizationIdAndVehicleIdIsNull(orgId));
        }

        // Cache vehicle-level docs
        Set<UUID> vehicleIds = list.stream().map(v -> v.id).collect(Collectors.toSet());
        Map<UUID, List<CarDocument>> vehicleDocs = documents.findByVehicleIdIn(vehicleIds)
                .stream().filter(d -> d.vehicleId != null)
                .collect(Collectors.groupingBy(d -> d.vehicleId));

        return list.stream().map(v -> {
            OrgInfo org = orgCache.get(v.organizationId);
            String orgName = org != null ? org.name() : null;
            boolean orgSellable = org != null && Sellability.orgSellable(org, orgDocs.get(v.organizationId), today, settings.individualPartnersEnabled());
            List<CarDocument> vDocs = vehicleDocs.getOrDefault(v.id, List.of());
            List<DocumentType> missing = Sellability.missingVehicleDocuments(vDocs, today());
            boolean sellable = v.active && orgSellable && missing.isEmpty();
            return AdminVehicleResponse.of(v, orgName, branchNames.getOrDefault(v.homeBranchId, null),
                    sellable, missing);
        }).toList();
    }
}
