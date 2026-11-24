package com.waylo.hotelservice.publication;

import com.waylo.hotelservice.client.AccessDecision;
import com.waylo.hotelservice.client.AuthAccessClient;
import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.PublicationStatus;
import com.waylo.hotelservice.dto.HotelMapper;
import com.waylo.hotelservice.dto.HotelResponse;
import com.waylo.hotelservice.exception.AccessDeniedException;
import com.waylo.hotelservice.exception.AuthenticationRequiredException;
import com.waylo.hotelservice.exception.AuthorizationUnavailableException;
import com.waylo.hotelservice.exception.PublicationException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.publication.HotelPublicationDecision.ActorRole;
import com.waylo.hotelservice.publication.HotelPublicationDecision.Type;
import com.waylo.hotelservice.publication.HotelPublicationRequest.Kind;
import com.waylo.hotelservice.publication.PublicationDtos.*;
import com.waylo.hotelservice.publication.PublicationStateMachine.Command;
import com.waylo.hotelservice.repository.DailyInventoryRepository;
import com.waylo.hotelservice.repository.DailyRateRepository;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.HotelRoomTypeRepository;
import com.waylo.hotelservice.repository.RatePlanRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.waylo.hotelservice.domain.PublicationStatus.*;

/**
 * Hotel publication workflow (contract sec. 1, 3.3, 4). One transaction per public method. The partner
 * access guard must already have run in the controller (it switches the RLS context to partner mode).
 */
@Service
@Transactional
public class HotelPublicationService {

    static final List<String> CONTENT_KEYS =
            List.of("name", "city", "country", "address", "starRating", "description", "latitude", "longitude");

    private final HotelRepository hotelRepository;
    private final HotelPublicationRequestRepository requestRepository;
    private final HotelPublicationDecisionRepository decisionRepository;
    private final HotelContentRevisionRepository revisionRepository;
    private final HotelRoomTypeRepository roomTypeRepository;
    private final RatePlanRepository ratePlanRepository;
    private final DailyInventoryRepository inventoryRepository;
    private final DailyRateRepository rateRepository;
    private final AuthAccessClient authAccessClient;
    private final HotelMapper hotelMapper;
    private final Clock clock;

    public HotelPublicationService(HotelRepository hotelRepository,
                                   HotelPublicationRequestRepository requestRepository,
                                   HotelPublicationDecisionRepository decisionRepository,
                                   HotelContentRevisionRepository revisionRepository,
                                   HotelRoomTypeRepository roomTypeRepository,
                                   RatePlanRepository ratePlanRepository,
                                   DailyInventoryRepository inventoryRepository,
                                   DailyRateRepository rateRepository,
                                   AuthAccessClient authAccessClient,
                                   HotelMapper hotelMapper,
                                   Clock clock) {
        this.hotelRepository = hotelRepository;
        this.requestRepository = requestRepository;
        this.decisionRepository = decisionRepository;
        this.revisionRepository = revisionRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.ratePlanRepository = ratePlanRepository;
        this.inventoryRepository = inventoryRepository;
        this.rateRepository = rateRepository;
        this.authAccessClient = authAccessClient;
        this.hotelMapper = hotelMapper;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ partner: requests

    public RequestView submit(UUID userId, UUID hotelId, SubmitRequest body) {
        Hotel hotel = lockHotel(hotelId);
        UUID orgId = requireOrg(hotel);
        PublicationStatus status = hotel.getPublicationStatus();
        Instant now = Instant.now(clock);

        HotelPublicationRequest request = new HotelPublicationRequest();
        request.setHotelId(hotelId);
        request.setOrganizationId(orgId);
        request.setSubmittedBy(userId);
        request.setSubmittedAt(now);
        request.setSubmitNote(body == null ? null : body.note());

        if (status == PUBLISHED || status == SUSPENDED) {
            if (body == null || body.revisionId() == null || body.expectedVersion() == null) {
                throw PublicationException.validation("revisionId and expectedVersion are required for a content change");
            }
            if (requestRepository.existsByHotelIdAndStatus(hotelId, HotelPublicationRequest.Status.SUBMITTED)) {
                throw PublicationException.conflict("REQUEST_ALREADY_OPEN", "A publication request is already open");
            }
            HotelContentRevision revision = revisionRepository.findByIdAndHotelId(body.revisionId(), hotelId)
                    .orElseThrow(() -> new ResourceNotFoundException("Revision", "id", body.revisionId()));
            if (revision.getStatus() != HotelContentRevision.Status.DRAFT) {
                throw new PublicationException(409, "INVALID_STATE_TRANSITION", "Revision is not a draft",
                        Map.of("from", revision.getStatus().name(), "to", "SUBMITTED"));
            }
            if (revision.getVersion() != body.expectedVersion()) {
                throw versionConflict(revision.getVersion());
            }
            if (sameContent(revision.getContent(), liveContent(hotel))) {
                throw PublicationException.conflict("REVISION_EMPTY", "Revision is identical to the live content");
            }
            requireOrganizationActive(orgId);
            request.setKind(Kind.CONTENT_CHANGE);
            request.setRevisionId(revision.getId());
            revision.setStatus(HotelContentRevision.Status.SUBMITTED);
            revision.setUpdatedAt(now);
            revisionRepository.save(revision);
            request = saveRequest(request);
            recordDecision(hotel, request.getId(), Type.SUBMIT, status, status, userId, ActorRole.PARTNER, null);
            return requestView(request, status);
        }

        // INITIAL: DRAFT or REJECTED only, everything else is an invalid transition.
        PublicationStatus to = PublicationStateMachine.apply(status, Command.SUBMIT);
        if (body != null && (body.revisionId() != null || body.expectedVersion() != null)) {
            throw PublicationException.validation("revisionId and expectedVersion are not allowed for an initial submission");
        }
        requireComplete(hotelId);
        requireOrganizationActive(orgId);
        request.setKind(Kind.INITIAL);
        request = saveRequest(request);
        hotel.setPublicationStatus(to);
        hotelRepository.save(hotel);
        recordDecision(hotel, request.getId(), Type.SUBMIT, status, to, userId, ActorRole.PARTNER, null);
        return requestView(request, to);
    }

    @Transactional(readOnly = true)
    public List<PartnerRequestItem> listForHotel(UUID hotelId) {
        findHotel(hotelId);
        return requestRepository.findByHotelIdOrderBySubmittedAtDesc(hotelId).stream().map(r -> {
            DecisionView latest = decisionRepository.findByRequestIdOrderByCreatedAtDesc(r.getId()).stream()
                    .filter(d -> d.getActorRole() == ActorRole.ADMIN)
                    .findFirst()
                    .map(d -> new DecisionView(d.getDecision().name(), d.getReason(), d.getActorRole().name(), d.getCreatedAt()))
                    .orElse(null);
            return new PartnerRequestItem(r.getId(), r.getKind().name(), r.getStatus().name(), r.getSubmittedAt(),
                    r.getDecidedAt(), latest);
        }).toList();
    }

    public RequestView withdraw(UUID userId, UUID hotelId, UUID requestId) {
        Hotel hotel = lockHotel(hotelId);
        HotelPublicationRequest request = requestRepository.findByIdAndHotelId(requestId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("PublicationRequest", "id", requestId));
        if (request.getStatus() != HotelPublicationRequest.Status.SUBMITTED) {
            throw new PublicationException(409, "INVALID_STATE_TRANSITION", "Only a submitted request can be withdrawn",
                    Map.of("from", request.getStatus().name(), "to", "WITHDRAWN"));
        }
        Instant now = Instant.now(clock);
        PublicationStatus from = hotel.getPublicationStatus();
        PublicationStatus to = from;
        if (request.getKind() == Kind.INITIAL) {
            to = PublicationStateMachine.apply(from, Command.WITHDRAW);
            hotel.setPublicationStatus(to);
            hotelRepository.save(hotel);
        } else {
            UUID revisionIdOf = request.getRevisionId();
            HotelContentRevision revision = revisionRepository.findById(request.getRevisionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Revision", "id", revisionIdOf));
            revision.setStatus(HotelContentRevision.Status.DRAFT);
            revision.setUpdatedAt(now);
            revisionRepository.save(revision);
        }
        request.setStatus(HotelPublicationRequest.Status.WITHDRAWN);
        request.setDecidedAt(now);
        request = requestRepository.save(request);
        recordDecision(hotel, request.getId(), Type.WITHDRAW, from, to, userId, ActorRole.PARTNER, null);
        return requestView(request, to);
    }

    public HotelResponse archiveByPartner(UUID userId, UUID hotelId, String reason) {
        Hotel hotel = lockHotel(hotelId);
        requireOrg(hotel);
        String cleaned = PublicationStateMachine.requireReason(reason);
        PublicationStatus from = hotel.getPublicationStatus();
        PublicationStatus to = PublicationStateMachine.apply(from, Command.ARCHIVE_PARTNER);
        hotel.setPublicationStatus(to);
        hotelRepository.save(hotel);
        recordDecision(hotel, null, Type.ARCHIVE, from, to, userId, ActorRole.PARTNER, cleaned);
        return hotelMapper.toResponse(hotel);
    }

    // ------------------------------------------------------------------ partner: content revision

    @Transactional(readOnly = true)
    public ContentView getContent(UUID hotelId) {
        Hotel hotel = findHotel(hotelId);
        RevisionView revision = revisionRepository.findWorking(hotelId).map(this::revisionView).orElse(null);
        return new ContentView(hotelId, hotel.getPublicationStatus().name(), hotel.getContentVersion(),
                liveContent(hotel), revision);
    }

    public RevisionView patchContent(UUID userId, UUID hotelId, PatchContentRequest body) {
        if (body == null || body.expectedVersion() == null || body.content() == null || body.content().isEmpty()) {
            throw PublicationException.validation("expectedVersion and a non empty content are required");
        }
        Map<String, Object> patch = validateContent(body.content());
        Hotel hotel = lockHotel(hotelId);
        UUID orgId = requireOrg(hotel);
        assertRevisionAllowed(hotel.getPublicationStatus());
        Instant now = Instant.now(clock);

        HotelContentRevision revision = revisionRepository.findWorking(hotelId).orElse(null);
        if (revision != null) {
            if (revision.getStatus() == HotelContentRevision.Status.SUBMITTED) {
                throw PublicationException.conflict("HOTEL_LOCKED_FOR_REVIEW", "Revision is under review");
            }
            if (revision.getVersion() != body.expectedVersion()) {
                throw versionConflict(revision.getVersion());
            }
            Map<String, Object> merged = new LinkedHashMap<>(revision.getContent());
            merged.putAll(patch);
            revision.setContent(merged);
            revision.setUpdatedAt(now);
        } else {
            if (hotel.getContentVersion() != body.expectedVersion()) {
                throw versionConflict(hotel.getContentVersion());
            }
            Map<String, Object> merged = new LinkedHashMap<>(liveContent(hotel));
            merged.putAll(patch);
            revision = new HotelContentRevision();
            revision.setHotelId(hotelId);
            revision.setOrganizationId(orgId);
            revision.setRevisionNo(revisionRepository.maxRevisionNo(hotelId) + 1);
            revision.setContent(merged);
            revision.setBaseContentVersion(hotel.getContentVersion());
            revision.setCreatedBy(userId);
            revision.setCreatedAt(now);
            revision.setUpdatedAt(now);
        }
        return revisionView(revisionRepository.saveAndFlush(revision));
    }

    public void discardRevision(UUID hotelId) {
        Hotel hotel = lockHotel(hotelId);
        requireOrg(hotel);
        HotelContentRevision revision = revisionRepository.findWorking(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Revision", "hotelId", hotelId));
        if (revision.getStatus() == HotelContentRevision.Status.SUBMITTED) {
            throw PublicationException.conflict("HOTEL_LOCKED_FOR_REVIEW", "Revision is under review, withdraw the request first");
        }
        revisionRepository.delete(revision);
    }

    // ------------------------------------------------------------------ hotel level guards used by HotelController

    /** PUT /api/hotels/{id} gate (contract sec. 3.3 and organization hand-off hole). */
    @Transactional(readOnly = true)
    public void assertDirectEditable(UUID hotelId, UUID requestedOrganizationId) {
        Hotel hotel = findHotel(hotelId);
        if (requestedOrganizationId != null && !requestedOrganizationId.equals(hotel.getOrganizationId())) {
            throw new PublicationException(400, "ORGANIZATION_CHANGE_NOT_ALLOWED",
                    "The organization of an existing hotel cannot be changed");
        }
        switch (hotel.getPublicationStatus()) {
            case DRAFT, REJECTED -> { }
            case SUBMITTED, APPROVED -> throw PublicationException.conflict("HOTEL_LOCKED_FOR_REVIEW",
                    "Hotel is locked while it is under review");
            case PUBLISHED, SUSPENDED -> throw PublicationException.conflict("CONTENT_REQUIRES_REVISION",
                    "Published content changes go through a content revision");
            case ARCHIVED -> throw PublicationException.conflict("HOTEL_ARCHIVED", "Hotel is archived");
        }
    }

    /** DELETE /api/hotels/{id} gate: only a DRAFT hotel without any publication history. */
    @Transactional(readOnly = true)
    public void assertDeletable(UUID hotelId) {
        Hotel hotel = findHotel(hotelId);
        if (hotel.getPublicationStatus() != DRAFT
                || requestRepository.existsByHotelId(hotelId)
                || decisionRepository.existsByHotelId(hotelId)
                || revisionRepository.existsByHotelId(hotelId)) {
            throw PublicationException.conflict("HOTEL_HAS_HISTORY", "Hotel has publication history, archive it instead");
        }
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public AdminPage adminList(String status, String kind, UUID hotelId, int page, int size) {
        HotelPublicationRequest.Status st = parseEnum(HotelPublicationRequest.Status.class,
                status == null || status.isBlank() ? "SUBMITTED" : status, "status");
        Kind kd = kind == null || kind.isBlank() ? null : parseEnum(Kind.class, kind, "kind");
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Specification<HotelPublicationRequest> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("status"), st));
            if (kd != null) p.add(cb.equal(root.get("kind"), kd));
            if (hotelId != null) p.add(cb.equal(root.get("hotelId"), hotelId));
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<HotelPublicationRequest> result = requestRepository.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by("submittedAt").ascending()));
        Map<UUID, Hotel> hotels = hotelRepository.findAllById(
                result.getContent().stream().map(HotelPublicationRequest::getHotelId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Hotel::getId, Function.identity()));
        List<AdminItem> items = result.getContent().stream().map(r -> {
            Hotel h = hotels.get(r.getHotelId());
            return new AdminItem(r.getId(), r.getHotelId(), h == null ? null : h.getName(), r.getOrganizationId(),
                    r.getKind().name(), r.getStatus().name(), r.getSubmittedBy(), r.getSubmittedAt(), r.getSubmitNote(),
                    h == null ? null : h.getPublicationStatus().name(), r.getVersion());
        }).toList();
        return new AdminPage(items, safePage, safeSize, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminDetail adminDetail(UUID requestId) {
        HotelPublicationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("PublicationRequest", "id", requestId));
        Hotel hotel = findHotel(request.getHotelId());
        RevisionView revision = null;
        List<DiffEntry> diff = List.of();
        if (request.getRevisionId() != null) {
            HotelContentRevision rev = revisionRepository.findById(request.getRevisionId()).orElse(null);
            if (rev != null) {
                revision = revisionView(rev);
                diff = diff(liveContent(hotel), rev.getContent());
            }
        }
        List<AdminDecisionEntry> decisions = decisionRepository.findByHotelIdOrderByCreatedAtAsc(hotel.getId()).stream()
                .map(d -> new AdminDecisionEntry(d.getId(), d.getRequestId(), d.getDecision().name(),
                        d.getFromStatus() == null ? null : d.getFromStatus().name(), d.getToStatus().name(),
                        d.getActorUserId(), d.getActorRole().name(), d.getReason(), d.getCreatedAt()))
                .toList();
        return new AdminDetail(requestView(request, hotel.getPublicationStatus()), hotelMapper.toResponse(hotel),
                revision, diff, decisions);
    }

    public RequestView adminDecide(UUID adminId, UUID requestId, AdminDecisionRequest body) {
        if (adminId == null) {
            throw new AuthenticationRequiredException("Authentication required");
        }
        if (body == null || body.decision() == null || body.expectedVersion() == null) {
            throw PublicationException.validation("decision and expectedVersion are required");
        }
        Command command = switch (body.decision()) {
            case "APPROVE" -> Command.APPROVE;
            case "REJECT" -> Command.REJECT;
            case "APPROVE_AND_PUBLISH" -> Command.APPROVE_AND_PUBLISH;
            default -> throw PublicationException.validation("decision must be APPROVE, REJECT or APPROVE_AND_PUBLISH");
        };
        String reason = command == Command.REJECT ? PublicationStateMachine.requireReason(body.reason())
                : (body.reason() == null || body.reason().isBlank() ? null : body.reason().trim());

        HotelPublicationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("PublicationRequest", "id", requestId));
        Hotel hotel = lockHotel(request.getHotelId());
        if (request.getStatus() != HotelPublicationRequest.Status.SUBMITTED) {
            throw PublicationException.conflict("REQUEST_NOT_OPEN", "Request is not open");
        }
        if (request.getVersion() != body.expectedVersion()) {
            throw versionConflict(request.getVersion());
        }
        requireNotSelfReview(adminId, request.getOrganizationId());

        Instant now = Instant.now(clock);
        PublicationStatus from = hotel.getPublicationStatus();
        PublicationStatus end = from;

        if (request.getKind() == Kind.INITIAL) {
            PublicationStateMachine.apply(from, command);
            if (command == Command.REJECT) {
                hotel.setPublicationStatus(REJECTED);
                request.setStatus(HotelPublicationRequest.Status.REJECTED);
                recordDecision(hotel, requestId, Type.REJECT, from, REJECTED, adminId, ActorRole.ADMIN, reason);
                end = REJECTED;
            } else {
                request.setStatus(HotelPublicationRequest.Status.APPROVED);
                recordDecision(hotel, requestId, Type.APPROVE, from, APPROVED, adminId, ActorRole.ADMIN, reason);
                end = APPROVED;
                if (command == Command.APPROVE_AND_PUBLISH) {
                    PublicationStatus published = PublicationStateMachine.apply(APPROVED, Command.PUBLISH);
                    hotel.setPublishedAt(now);
                    recordDecision(hotel, requestId, Type.PUBLISH, APPROVED, published, adminId, ActorRole.ADMIN, null);
                    end = published;
                }
                hotel.setPublicationStatus(end);
            }
        } else {
            if (from != PUBLISHED && from != SUSPENDED) {
                throw new PublicationException(409, "INVALID_STATE_TRANSITION", "Content change on a hotel in status " + from,
                        Map.of("from", from.name(), "to", from.name()));
            }
            UUID revisionIdOf = request.getRevisionId();
            HotelContentRevision revision = revisionRepository.findById(request.getRevisionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Revision", "id", revisionIdOf));
            if (command == Command.REJECT) {
                revision.setStatus(HotelContentRevision.Status.REJECTED);
                revision.setUpdatedAt(now);
                request.setStatus(HotelPublicationRequest.Status.REJECTED);
                recordDecision(hotel, requestId, Type.REJECT, from, from, adminId, ActorRole.ADMIN, reason);
            } else {
                if (revision.getBaseContentVersion() != hotel.getContentVersion()) {
                    throw PublicationException.conflict("STALE_REVISION", "Live content changed since this revision was created");
                }
                applyContent(hotel, revision.getContent());
                if (hotel.getPublishedRevisionId() != null) {
                    revisionRepository.findById(hotel.getPublishedRevisionId()).ifPresent(prev -> {
                        prev.setStatus(HotelContentRevision.Status.SUPERSEDED);
                        prev.setAppliedAt(null);
                        prev.setUpdatedAt(now);
                        revisionRepository.save(prev);
                    });
                }
                revision.setStatus(HotelContentRevision.Status.APPLIED);
                revision.setAppliedAt(now);
                revision.setUpdatedAt(now);
                hotel.setPublishedRevisionId(revision.getId());
                hotel.setContentVersion(hotel.getContentVersion() + 1);
                request.setStatus(HotelPublicationRequest.Status.APPROVED);
                recordDecision(hotel, requestId, Type.APPROVE, from, from, adminId, ActorRole.ADMIN, reason);
            }
            revisionRepository.save(revision);
        }
        request.setDecidedAt(now);
        hotelRepository.save(hotel);
        request = requestRepository.saveAndFlush(request);
        return requestView(request, end);
    }

    public HotelResponse adminSetStatus(UUID adminId, UUID hotelId, AdminStatusRequest body) {
        if (adminId == null) {
            throw new AuthenticationRequiredException("Authentication required");
        }
        if (body == null || body.decision() == null) {
            throw PublicationException.validation("decision is required");
        }
        Command command;
        Type type;
        switch (body.decision()) {
            case "PUBLISH" -> { command = Command.PUBLISH; type = Type.PUBLISH; }
            case "SUSPEND" -> { command = Command.SUSPEND; type = Type.SUSPEND; }
            case "REINSTATE" -> { command = Command.REINSTATE; type = Type.REINSTATE; }
            case "ARCHIVE" -> { command = Command.ARCHIVE_ADMIN; type = Type.ARCHIVE; }
            default -> throw PublicationException.validation("decision must be PUBLISH, SUSPEND, REINSTATE or ARCHIVE");
        }
        String reason = command == Command.PUBLISH
                ? (body.reason() == null || body.reason().isBlank() ? null : body.reason().trim())
                : PublicationStateMachine.requireReason(body.reason());
        Hotel hotel = lockHotel(hotelId);
        PublicationStatus from = hotel.getPublicationStatus();
        PublicationStatus to = PublicationStateMachine.apply(from, command);
        hotel.setPublicationStatus(to);
        if (command == Command.PUBLISH) {
            hotel.setPublishedAt(Instant.now(clock));
        }
        hotelRepository.save(hotel);
        recordDecision(hotel, null, type, from, to, adminId, ActorRole.ADMIN, reason);
        return hotelMapper.toResponse(hotel);
    }

    // ------------------------------------------------------------------ helpers

    private Hotel findHotel(UUID hotelId) {
        return hotelRepository.findById(hotelId).orElseThrow(() -> new ResourceNotFoundException("Hotel", "id", hotelId));
    }

    private Hotel lockHotel(UUID hotelId) {
        return hotelRepository.findByIdForUpdate(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", "id", hotelId));
    }

    private static UUID requireOrg(Hotel hotel) {
        if (hotel.getOrganizationId() == null) {
            throw new AccessDeniedException("Hotel has no owning organization; changes are locked until it is assigned");
        }
        return hotel.getOrganizationId();
    }

    private static void assertRevisionAllowed(PublicationStatus status) {
        switch (status) {
            case DRAFT, REJECTED -> throw PublicationException.conflict("USE_DIRECT_EDIT", "Edit the hotel directly while it is a draft");
            case SUBMITTED, APPROVED -> throw PublicationException.conflict("HOTEL_LOCKED_FOR_REVIEW", "Hotel is locked while it is under review");
            case ARCHIVED -> throw PublicationException.conflict("HOTEL_ARCHIVED", "Hotel is archived");
            case PUBLISHED, SUSPENDED -> { }
        }
    }

    private void requireComplete(UUID hotelId) {
        List<String> missing = new ArrayList<>();
        if (roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(hotelId, CatalogStatus.ACTIVE).isEmpty()) {
            missing.add("ROOM_TYPE");
        }
        if (ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(hotelId, CatalogStatus.ACTIVE).isEmpty()) {
            missing.add("RATE_PLAN");
        }
        if (!inventoryRepository.existsByHotelId(hotelId) && !rateRepository.existsByHotelId(hotelId)) {
            missing.add("INVENTORY");
        }
        if (!missing.isEmpty()) {
            throw new PublicationException(409, "HOTEL_INCOMPLETE", "Hotel is not ready for review",
                    Map.of("missing", missing));
        }
    }

    private void requireOrganizationActive(UUID organizationId) {
        List<UUID> active;
        try {
            active = authAccessClient.activeOrganizationIds();
        } catch (RuntimeException e) {
            throw new AuthorizationUnavailableException("Authorization service unavailable");
        }
        if (active == null || !active.contains(organizationId)) {
            throw PublicationException.conflict("ORGANIZATION_NOT_ACTIVE", "Organization is not active");
        }
    }

    private void requireNotSelfReview(UUID adminId, UUID organizationId) {
        AccessDecision decision;
        try {
            decision = authAccessClient.check(adminId, organizationId, null, "HOTEL_READ");
        } catch (RuntimeException e) {
            throw new AuthorizationUnavailableException("Authorization service unavailable");
        }
        if (decision != null && decision.allowed()) {
            throw PublicationException.conflict("SELF_REVIEW_NOT_ALLOWED",
                    "An administrator cannot review a hotel of an organization they belong to");
        }
    }

    private HotelPublicationRequest saveRequest(HotelPublicationRequest request) {
        try {
            return requestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            throw PublicationException.conflict("REQUEST_ALREADY_OPEN", "A publication request is already open");
        }
    }

    private void recordDecision(Hotel hotel, UUID requestId, Type type, PublicationStatus from, PublicationStatus to,
                                UUID actor, ActorRole role, String reason) {
        decisionRepository.save(new HotelPublicationDecision(hotel.getId(), hotel.getOrganizationId(), requestId, type,
                from, to, actor, role, reason, Instant.now(clock)));
    }

    private static PublicationException versionConflict(long current) {
        return new PublicationException(409, "VERSION_CONFLICT", "Version is stale", Map.of("currentVersion", current));
    }

    private static RequestView requestView(HotelPublicationRequest r, PublicationStatus hotelStatus) {
        return new RequestView(r.getId(), r.getHotelId(), r.getOrganizationId(), r.getKind().name(), r.getStatus().name(),
                r.getRevisionId(), r.getSubmittedBy(), r.getSubmittedAt(), r.getSubmitNote(), r.getVersion(),
                hotelStatus.name());
    }

    private RevisionView revisionView(HotelContentRevision r) {
        return new RevisionView(r.getId(), r.getRevisionNo(), r.getStatus().name(), r.getVersion(),
                r.getBaseContentVersion(), r.getContent());
    }

    static Map<String, Object> liveContent(Hotel h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", h.getName());
        m.put("city", h.getCity());
        m.put("country", h.getCountry());
        m.put("address", h.getAddress());
        m.put("starRating", h.getStarRating());
        m.put("description", h.getDescription());
        m.put("latitude", h.getLatitude());
        m.put("longitude", h.getLongitude());
        return m;
    }

    private static void applyContent(Hotel h, Map<String, Object> c) {
        h.setName((String) c.get("name"));
        h.setCity((String) c.get("city"));
        h.setCountry((String) c.get("country"));
        h.setAddress((String) c.get("address"));
        h.setStarRating(((Number) c.get("starRating")).intValue());
        h.setDescription((String) c.get("description"));
        h.setLatitude(decimal(c.get("latitude")));
        h.setLongitude(decimal(c.get("longitude")));
    }

    private static BigDecimal decimal(Object o) {
        return o == null ? null : new BigDecimal(o.toString());
    }

    static boolean sameContent(Map<String, Object> a, Map<String, Object> b) {
        for (String key : CONTENT_KEYS) {
            if (!sameValue(a.get(key), b.get(key))) return false;
        }
        return true;
    }

    private static boolean sameValue(Object a, Object b) {
        if (a == null || b == null) return a == b;
        if (a instanceof Number || b instanceof Number) {
            if (!(a instanceof Number) || !(b instanceof Number)) return false;
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
        }
        return a.equals(b);
    }

    static List<DiffEntry> diff(Map<String, Object> live, Map<String, Object> revision) {
        List<DiffEntry> out = new ArrayList<>();
        for (String key : CONTENT_KEYS) {
            if (!sameValue(live.get(key), revision.get(key))) {
                out.add(new DiffEntry(key, live.get(key), revision.get(key)));
            }
        }
        return out;
    }

    /** Allowlist and type/range validation (contract sec. 3.2). Unknown keys are rejected. */
    static Map<String, Object> validateContent(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (!CONTENT_KEYS.contains(k)) {
                throw PublicationException.validation("Unknown content field: " + k);
            }
            switch (k) {
                case "name", "city", "country" -> out.put(k, text(k, v, 1, 255, false));
                case "address" -> out.put(k, text(k, v, 1, 500, false));
                case "description" -> out.put(k, text(k, v, 0, 5000, true));
                case "starRating" -> {
                    if (!(v instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())
                            || n.intValue() < 1 || n.intValue() > 5) {
                        throw PublicationException.validation("starRating must be an integer between 1 and 5");
                    }
                    out.put(k, n.intValue());
                }
                case "latitude" -> out.put(k, coordinate(k, v, 90));
                case "longitude" -> out.put(k, coordinate(k, v, 180));
                default -> throw PublicationException.validation("Unknown content field: " + k);
            }
        }
        return out;
    }

    private static String text(String key, Object v, int min, int max, boolean nullable) {
        if (v == null && nullable) return null;
        if (!(v instanceof String s) || s.strip().length() < Math.max(min, 0) || (min > 0 && s.isBlank()) || s.length() > max) {
            throw PublicationException.validation(key + " must be a text of " + min + " to " + max + " characters");
        }
        return s;
    }

    private static Object coordinate(String key, Object v, int limit) {
        if (v == null) return null;
        if (!(v instanceof Number n) || Math.abs(n.doubleValue()) > limit) {
            throw PublicationException.validation(key + " must be a number between -" + limit + " and " + limit);
        }
        return new BigDecimal(n.toString());
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String name) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw PublicationException.validation("Invalid " + name + ": " + value);
        }
    }
}
