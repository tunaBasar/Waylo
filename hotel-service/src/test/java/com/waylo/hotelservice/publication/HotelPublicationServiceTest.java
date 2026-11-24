package com.waylo.hotelservice.publication;

import com.waylo.hotelservice.client.AccessDecision;
import com.waylo.hotelservice.client.AuthAccessClient;
import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.HotelRoomType;
import com.waylo.hotelservice.domain.PublicationStatus;
import com.waylo.hotelservice.domain.RatePlan;
import com.waylo.hotelservice.dto.HotelMapper;
import com.waylo.hotelservice.exception.AccessDeniedException;
import com.waylo.hotelservice.exception.AuthorizationUnavailableException;
import com.waylo.hotelservice.exception.PublicationException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.media.HotelMedia;
import com.waylo.hotelservice.media.HotelMediaDecision;
import com.waylo.hotelservice.media.HotelMediaDecisionRepository;
import com.waylo.hotelservice.media.HotelMediaRepository;
import org.springframework.test.util.ReflectionTestUtils;
import com.waylo.hotelservice.publication.HotelPublicationDecision.ActorRole;
import com.waylo.hotelservice.publication.HotelPublicationDecision.Type;
import com.waylo.hotelservice.publication.PublicationDtos.*;
import com.waylo.hotelservice.repository.DailyInventoryRepository;
import com.waylo.hotelservice.repository.DailyRateRepository;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.HotelRoomTypeRepository;
import com.waylo.hotelservice.repository.RatePlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HotelPublicationServiceTest {

    @Mock HotelRepository hotelRepository;
    @Mock HotelPublicationRequestRepository requestRepository;
    @Mock HotelPublicationDecisionRepository decisionRepository;
    @Mock HotelContentRevisionRepository revisionRepository;
    @Mock HotelRoomTypeRepository roomTypeRepository;
    @Mock RatePlanRepository ratePlanRepository;
    @Mock DailyInventoryRepository inventoryRepository;
    @Mock DailyRateRepository rateRepository;
    @Mock AuthAccessClient authAccessClient;
    @Mock HotelMapper hotelMapper;
    @Mock HotelPublicationRequestMediaRepository linkRepository;
    @Mock HotelMediaRepository mediaRepository;
    @Mock HotelMediaDecisionRepository mediaDecisionRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC);
    private final UUID hotelId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID partnerId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    private HotelPublicationService service;
    private Hotel hotel;

    @BeforeEach
    void setUp() {
        service = new HotelPublicationService(hotelRepository, requestRepository, decisionRepository, revisionRepository,
                roomTypeRepository, ratePlanRepository, inventoryRepository, rateRepository, authAccessClient,
                hotelMapper, new PublicationMediaLinks(linkRepository, mediaRepository, mediaDecisionRepository), clock);
        hotel = hotel(PublicationStatus.DRAFT);
        when(hotelRepository.findById(hotelId)).thenAnswer(i -> Optional.of(hotel));
        when(hotelRepository.findByIdForUpdate(hotelId)).thenAnswer(i -> Optional.of(hotel));
        when(hotelRepository.save(any(Hotel.class))).thenAnswer(i -> i.getArgument(0));
        when(requestRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(requestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(revisionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(revisionRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(authAccessClient.activeOrganizationIds()).thenReturn(List.of(orgId));
        when(authAccessClient.check(any(), any(), any(), eq("HOTEL_READ"))).thenReturn(new AccessDecision(false, List.of()));
        complete();
    }

    private Hotel hotel(PublicationStatus status) {
        Hotel h = new Hotel();
        h.setId(hotelId);
        h.setOrganizationId(orgId);
        h.setName("Grand");
        h.setCity("Antalya");
        h.setCountry("TR");
        h.setAddress("Sahil 1");
        h.setStarRating(4);
        h.setDescription("old");
        h.setLatitude(new BigDecimal("36.5"));
        h.setPublicationStatus(status);
        return h;
    }

    private void complete() {
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(hotelId, CatalogStatus.ACTIVE)).thenReturn(List.of(new HotelRoomType()));
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(hotelId, CatalogStatus.ACTIVE)).thenReturn(List.of(new RatePlan()));
        when(inventoryRepository.existsByHotelId(hotelId)).thenReturn(true);
    }

    private HotelPublicationRequest openRequest(HotelPublicationRequest.Kind kind, UUID revisionId) {
        HotelPublicationRequest r = new HotelPublicationRequest();
        r.setHotelId(hotelId);
        r.setOrganizationId(orgId);
        r.setKind(kind);
        r.setRevisionId(revisionId);
        r.setSubmittedBy(partnerId);
        r.setSubmittedAt(Instant.now(clock));
        when(requestRepository.findById(any())).thenReturn(Optional.of(r));
        when(requestRepository.findByIdAndHotelId(any(), eq(hotelId))).thenReturn(Optional.of(r));
        return r;
    }

    private HotelPublicationDecision lastDecision(int times, int index) {
        ArgumentCaptor<HotelPublicationDecision> c = ArgumentCaptor.forClass(HotelPublicationDecision.class);
        verify(decisionRepository, org.mockito.Mockito.times(times)).save(c.capture());
        return c.getAllValues().get(index);
    }

    private HotelContentRevision revision(HotelContentRevision.Status status, long base) {
        HotelContentRevision r = new HotelContentRevision();
        r.setHotelId(hotelId);
        r.setOrganizationId(orgId);
        r.setRevisionNo(1);
        r.setStatus(status);
        r.setBaseContentVersion(base);
        Map<String, Object> c = new LinkedHashMap<>(HotelPublicationService.liveContent(hotel));
        c.put("description", "new text");
        r.setContent(c);
        return r;
    }

    // ---- submit (U3, T2)

    @Test
    void submitInitial_movesDraftToSubmitted_andWritesSubmitDecision() {
        RequestView view = service.submit(partnerId, hotelId, new SubmitRequest("hazir", null, null, null));

        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.SUBMITTED);
        assertThat(view.kind()).isEqualTo("INITIAL");
        assertThat(view.hotelPublicationStatus()).isEqualTo("SUBMITTED");
        HotelPublicationDecision d = lastDecision(1, 0);
        assertThat(d.getDecision()).isEqualTo(Type.SUBMIT);
        assertThat(d.getFromStatus()).isEqualTo(PublicationStatus.DRAFT);
        assertThat(d.getToStatus()).isEqualTo(PublicationStatus.SUBMITTED);
        assertThat(d.getActorUserId()).isEqualTo(partnerId);
        assertThat(d.getActorRole()).isEqualTo(ActorRole.PARTNER);
    }

    @Test
    void submitInitial_fromRejected_isAllowed() {
        hotel.setPublicationStatus(PublicationStatus.REJECTED);
        service.submit(partnerId, hotelId, null);
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.SUBMITTED);
    }

    @Test
    void submitInitial_fromSubmittedApprovedArchived_isInvalidTransition() {
        for (PublicationStatus s : List.of(PublicationStatus.SUBMITTED, PublicationStatus.APPROVED, PublicationStatus.ARCHIVED)) {
            hotel.setPublicationStatus(s);
            assertThatThrownBy(() -> service.submit(partnerId, hotelId, null))
                    .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_STATE_TRANSITION"));
        }
    }

    @Test
    void submitInitial_incompleteHotel_listsMissing() {
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(hotelId, CatalogStatus.ACTIVE)).thenReturn(List.of());
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(hotelId, CatalogStatus.ACTIVE)).thenReturn(List.of());
        when(inventoryRepository.existsByHotelId(hotelId)).thenReturn(false);
        when(rateRepository.existsByHotelId(hotelId)).thenReturn(false);

        assertThatThrownBy(() -> service.submit(partnerId, hotelId, null))
                .isInstanceOfSatisfying(PublicationException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo("HOTEL_INCOMPLETE");
                    assertThat(e.getDetails().get("missing")).isEqualTo(List.of("ROOM_TYPE", "RATE_PLAN", "INVENTORY"));
                });
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.DRAFT);
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void submitInitial_rateRowAloneSatisfiesInventoryGate() {
        when(inventoryRepository.existsByHotelId(hotelId)).thenReturn(false);
        when(rateRepository.existsByHotelId(hotelId)).thenReturn(true);
        service.submit(partnerId, hotelId, null);
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.SUBMITTED);
    }

    @Test
    void submitInitial_orgNotActive_409() {
        when(authAccessClient.activeOrganizationIds()).thenReturn(List.of());
        assertThatThrownBy(() -> service.submit(partnerId, hotelId, null))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getCode()).isEqualTo("ORGANIZATION_NOT_ACTIVE"));
    }

    @Test
    void submitInitial_authDown_failsClosedAsAuthorizationUnavailable() {
        when(authAccessClient.activeOrganizationIds()).thenThrow(new RuntimeException("down"));
        assertThatThrownBy(() -> service.submit(partnerId, hotelId, null)).isInstanceOf(AuthorizationUnavailableException.class);
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.DRAFT);
    }

    @Test
    void submitInitial_secondOpenRequest_mapsConstraintToRequestAlreadyOpen() {
        when(requestRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_hpr_one_open_per_hotel"));
        assertThatThrownBy(() -> service.submit(partnerId, hotelId, null))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getCode()).isEqualTo("REQUEST_ALREADY_OPEN"));
    }

    @Test
    void submitInitial_rejectsRevisionFields() {
        assertThatThrownBy(() -> service.submit(partnerId, hotelId, new SubmitRequest(null, UUID.randomUUID(), 0L, null)))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
    }

    @Test
    void submit_legacyHotelWithoutOrg_forbidden() {
        hotel.setOrganizationId(null);
        assertThatThrownBy(() -> service.submit(partnerId, hotelId, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void submit_unknownHotel_404() {
        UUID other = UUID.randomUUID();
        when(hotelRepository.findByIdForUpdate(other)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(partnerId, other, null)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitContentChange_requiresRevisionFields() {
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        assertThatThrownBy(() -> service.submit(partnerId, hotelId, new SubmitRequest(null, null, null, null)))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
    }

    @Test
    void submitContentChange_staleVersion_409WithCurrentVersion() {
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        HotelContentRevision rev = revision(HotelContentRevision.Status.DRAFT, 0);
        UUID revId = UUID.randomUUID();
        when(revisionRepository.findByIdAndHotelId(revId, hotelId)).thenReturn(Optional.of(rev));

        assertThatThrownBy(() -> service.submit(partnerId, hotelId, new SubmitRequest(null, revId, 7L, null)))
                .isInstanceOfSatisfying(PublicationException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("VERSION_CONFLICT");
                    assertThat(e.getDetails()).containsEntry("currentVersion", 0L);
                });
    }

    @Test
    void submitContentChange_identicalToLive_revisionEmpty() {
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        HotelContentRevision rev = revision(HotelContentRevision.Status.DRAFT, 0);
        rev.setContent(new LinkedHashMap<>(HotelPublicationService.liveContent(hotel)));
        UUID revId = UUID.randomUUID();
        when(revisionRepository.findByIdAndHotelId(revId, hotelId)).thenReturn(Optional.of(rev));

        assertThatThrownBy(() -> service.submit(partnerId, hotelId, new SubmitRequest(null, revId, 0L, null)))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getCode()).isEqualTo("REVISION_EMPTY"));
    }

    @Test
    void submitContentChange_ok_keepsHotelStatusAndLocksRevision() {
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        HotelContentRevision rev = revision(HotelContentRevision.Status.DRAFT, 0);
        UUID revId = UUID.randomUUID();
        when(revisionRepository.findByIdAndHotelId(revId, hotelId)).thenReturn(Optional.of(rev));

        RequestView view = service.submit(partnerId, hotelId, new SubmitRequest("x", revId, 0L, null));

        assertThat(view.kind()).isEqualTo("CONTENT_CHANGE");
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(rev.getStatus()).isEqualTo(HotelContentRevision.Status.SUBMITTED);
    }

    // ---- withdraw (T3)

    @Test
    void withdrawInitial_backToDraft() {
        hotel.setPublicationStatus(PublicationStatus.SUBMITTED);
        HotelPublicationRequest r = openRequest(HotelPublicationRequest.Kind.INITIAL, null);

        RequestView view = service.withdraw(partnerId, hotelId, UUID.randomUUID());

        assertThat(view.status()).isEqualTo("WITHDRAWN");
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.DRAFT);
        assertThat(r.getDecidedAt()).isNotNull();
        assertThat(lastDecision(1, 0).getDecision()).isEqualTo(Type.WITHDRAW);
    }

    @Test
    void withdraw_requestOfAnotherHotel_404() {
        when(requestRepository.findByIdAndHotelId(any(), eq(hotelId))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.withdraw(partnerId, hotelId, UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void withdraw_notSubmitted_invalidTransition() {
        HotelPublicationRequest r = openRequest(HotelPublicationRequest.Kind.INITIAL, null);
        r.setStatus(HotelPublicationRequest.Status.REJECTED);
        assertThatThrownBy(() -> service.withdraw(partnerId, hotelId, UUID.randomUUID()))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_STATE_TRANSITION"));
    }

    @Test
    void withdrawContentChange_revisionBackToDraft_hotelUntouched() {
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        UUID revId = UUID.randomUUID();
        HotelContentRevision rev = revision(HotelContentRevision.Status.SUBMITTED, 0);
        when(revisionRepository.findById(revId)).thenReturn(Optional.of(rev));
        openRequest(HotelPublicationRequest.Kind.CONTENT_CHANGE, revId);

        service.withdraw(partnerId, hotelId, UUID.randomUUID());

        assertThat(rev.getStatus()).isEqualTo(HotelContentRevision.Status.DRAFT);
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
    }

    // ---- partner archive (T9)

    @Test
    void partnerArchive_draftOk_publishedRejected_reasonRequired() {
        service.archiveByPartner(partnerId, hotelId, "artik kullanilmiyor");
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.ARCHIVED);
        assertThat(lastDecision(1, 0).getDecision()).isEqualTo(Type.ARCHIVE);

        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        assertThatThrownBy(() -> service.archiveByPartner(partnerId, hotelId, "artik kullanilmiyor"))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_STATE_TRANSITION"));

        hotel.setPublicationStatus(PublicationStatus.DRAFT);
        assertThatThrownBy(() -> service.archiveByPartner(partnerId, hotelId, "no"))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
    }

    // ---- admin decide (T4..T6, T10, T11)

    @Test
    void adminApprove_initial_hotelApproved_requestApproved() {
        hotel.setPublicationStatus(PublicationStatus.SUBMITTED);
        HotelPublicationRequest r = openRequest(HotelPublicationRequest.Kind.INITIAL, null);

        RequestView view = service.adminDecide(adminId, UUID.randomUUID(), new AdminDecisionRequest("APPROVE", null, 0L));

        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.APPROVED);
        assertThat(view.status()).isEqualTo("APPROVED");
        assertThat(r.getDecidedAt()).isNotNull();
        HotelPublicationDecision d = lastDecision(1, 0);
        assertThat(d.getDecision()).isEqualTo(Type.APPROVE);
        assertThat(d.getActorRole()).isEqualTo(ActorRole.ADMIN);
        assertThat(d.getActorUserId()).isEqualTo(adminId);
    }

    @Test
    void adminApproveAndPublish_writesApproveThenPublishRows_andStampsPublishedAt() {
        hotel.setPublicationStatus(PublicationStatus.SUBMITTED);
        openRequest(HotelPublicationRequest.Kind.INITIAL, null);

        RequestView view = service.adminDecide(adminId, UUID.randomUUID(), new AdminDecisionRequest("APPROVE_AND_PUBLISH", null, 0L));

        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(hotel.getPublishedAt()).isEqualTo(Instant.now(clock));
        assertThat(view.hotelPublicationStatus()).isEqualTo("PUBLISHED");
        assertThat(lastDecision(2, 0).getDecision()).isEqualTo(Type.APPROVE);
        assertThat(lastDecision(2, 1).getDecision()).isEqualTo(Type.PUBLISH);
    }

    @Test
    void adminReject_requiresReason_thenStoresIt() {
        hotel.setPublicationStatus(PublicationStatus.SUBMITTED);
        openRequest(HotelPublicationRequest.Kind.INITIAL, null);

        assertThatThrownBy(() -> service.adminDecide(adminId, UUID.randomUUID(), new AdminDecisionRequest("REJECT", "kisa", 0L)))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.SUBMITTED);

        RequestView view = service.adminDecide(adminId, UUID.randomUUID(), new AdminDecisionRequest("REJECT", "Adres eksik", 0L));
        assertThat(view.status()).isEqualTo("REJECTED");
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.REJECTED);
        assertThat(lastDecision(1, 0).getReason()).isEqualTo("Adres eksik");
    }

    @Test
    void adminDecide_closedRequest_409RequestNotOpen_staleVersion_409VersionConflict() {
        hotel.setPublicationStatus(PublicationStatus.SUBMITTED);
        HotelPublicationRequest r = openRequest(HotelPublicationRequest.Kind.INITIAL, null);

        assertThatThrownBy(() -> service.adminDecide(adminId, UUID.randomUUID(), new AdminDecisionRequest("APPROVE", null, 9L)))
                .isInstanceOfSatisfying(PublicationException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo("VERSION_CONFLICT");
                    assertThat(e.getDetails()).containsEntry("currentVersion", 0L);
                });

        r.setStatus(HotelPublicationRequest.Status.APPROVED);
        assertThatThrownBy(() -> service.adminDecide(adminId, UUID.randomUUID(), new AdminDecisionRequest("APPROVE", null, 0L)))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getCode()).isEqualTo("REQUEST_NOT_OPEN"));
    }

    @Test
    void adminDecide_invalidDecisionOrMissingFields_400() {
        openRequest(HotelPublicationRequest.Kind.INITIAL, null);
        for (AdminDecisionRequest body : new AdminDecisionRequest[]{null, new AdminDecisionRequest("NOPE", null, 0L),
                new AdminDecisionRequest("APPROVE", null, null)}) {
            assertThatThrownBy(() -> service.adminDecide(adminId, UUID.randomUUID(), body))
                    .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        }
    }

    @Test
    void adminDecide_selfReview_blocked_andFailsClosedWhenAuthDown() {
        hotel.setPublicationStatus(PublicationStatus.SUBMITTED);
        openRequest(HotelPublicationRequest.Kind.INITIAL, null);
        when(authAccessClient.check(adminId, orgId, null, "HOTEL_READ")).thenReturn(new AccessDecision(true, List.of("OWNER")));

        assertThatThrownBy(() -> service.adminDecide(adminId, UUID.randomUUID(), new AdminDecisionRequest("APPROVE", null, 0L)))
                .isInstanceOfSatisfying(PublicationException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo("SELF_REVIEW_NOT_ALLOWED");
                });
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.SUBMITTED);

        when(authAccessClient.check(adminId, orgId, null, "HOTEL_READ")).thenThrow(new RuntimeException("down"));
        assertThatThrownBy(() -> service.adminDecide(adminId, UUID.randomUUID(), new AdminDecisionRequest("APPROVE", null, 0L)))
                .isInstanceOf(AuthorizationUnavailableException.class);
    }

    @Test
    void adminDecide_contentChangeApprove_appliesRevision_bumpsVersion_supersedesPrevious() {
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        hotel.setContentVersion(2);
        UUID previousId = UUID.randomUUID();
        hotel.setPublishedRevisionId(previousId);
        HotelContentRevision previous = revision(HotelContentRevision.Status.APPLIED, 1);
        previous.setAppliedAt(Instant.now(clock));
        when(revisionRepository.findById(previousId)).thenReturn(Optional.of(previous));
        UUID revId = UUID.randomUUID();
        HotelContentRevision rev = revision(HotelContentRevision.Status.SUBMITTED, 2);
        rev.getContent().put("starRating", 5);
        rev.getContent().put("latitude", new BigDecimal("37.1"));
        when(revisionRepository.findById(revId)).thenReturn(Optional.of(rev));
        openRequest(HotelPublicationRequest.Kind.CONTENT_CHANGE, revId);

        RequestView view = service.adminDecide(adminId, UUID.randomUUID(), new AdminDecisionRequest("APPROVE", null, 0L));

        assertThat(hotel.getDescription()).isEqualTo("new text");
        assertThat(hotel.getStarRating()).isEqualTo(5);
        assertThat(hotel.getLatitude()).isEqualByComparingTo("37.1");
        assertThat(hotel.getContentVersion()).isEqualTo(3);
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(hotel.getPublishedRevisionId()).isEqualTo(rev.getId());
        assertThat(rev.getStatus()).isEqualTo(HotelContentRevision.Status.APPLIED);
        assertThat(rev.getAppliedAt()).isNotNull();
        assertThat(previous.getStatus()).isEqualTo(HotelContentRevision.Status.SUPERSEDED);
        assertThat(view.status()).isEqualTo("APPROVED");
    }

    @Test
    void adminDecide_contentChangeApprove_staleBase_409StaleRevision() {
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        hotel.setContentVersion(5);
        UUID revId = UUID.randomUUID();
        when(revisionRepository.findById(revId)).thenReturn(Optional.of(revision(HotelContentRevision.Status.SUBMITTED, 2)));
        openRequest(HotelPublicationRequest.Kind.CONTENT_CHANGE, revId);

        assertThatThrownBy(() -> service.adminDecide(adminId, UUID.randomUUID(), new AdminDecisionRequest("APPROVE", null, 0L)))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getCode()).isEqualTo("STALE_REVISION"));
        assertThat(hotel.getDescription()).isEqualTo("old");
    }

    @Test
    void adminDecide_contentChangeReject_keepsLiveContentAndStatus() {
        hotel.setPublicationStatus(PublicationStatus.SUSPENDED);
        UUID revId = UUID.randomUUID();
        HotelContentRevision rev = revision(HotelContentRevision.Status.SUBMITTED, 0);
        when(revisionRepository.findById(revId)).thenReturn(Optional.of(rev));
        openRequest(HotelPublicationRequest.Kind.CONTENT_CHANGE, revId);

        RequestView view = service.adminDecide(adminId, UUID.randomUUID(), new AdminDecisionRequest("REJECT", "Uygunsuz metin", 0L));

        assertThat(view.status()).isEqualTo("REJECTED");
        assertThat(rev.getStatus()).isEqualTo(HotelContentRevision.Status.REJECTED);
        assertThat(hotel.getDescription()).isEqualTo("old");
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.SUSPENDED);
        assertThat(hotel.getContentVersion()).isZero();
    }

    // ---- admin status endpoint (T6, T7, T8, T9)

    @Test
    void adminSetStatus_suspendReinstatePublishArchive() {
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        service.adminSetStatus(adminId, hotelId, new AdminStatusRequest("SUSPEND", "Sikayet incelemesi"));
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.SUSPENDED);
        service.adminSetStatus(adminId, hotelId, new AdminStatusRequest("REINSTATE", "Temizlendi tamam"));
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);

        hotel.setPublicationStatus(PublicationStatus.APPROVED);
        service.adminSetStatus(adminId, hotelId, new AdminStatusRequest("PUBLISH", null));
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(hotel.getPublishedAt()).isNotNull();

        hotel.setPublicationStatus(PublicationStatus.SUSPENDED);
        service.adminSetStatus(adminId, hotelId, new AdminStatusRequest("ARCHIVE", "Kalici kapatma"));
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.ARCHIVED);

        ArgumentCaptor<HotelPublicationDecision> c = ArgumentCaptor.forClass(HotelPublicationDecision.class);
        verify(decisionRepository, org.mockito.Mockito.times(4)).save(c.capture());
        assertThat(c.getAllValues()).extracting(HotelPublicationDecision::getRequestId).containsOnlyNulls();
        assertThat(c.getAllValues()).extracting(HotelPublicationDecision::getDecision)
                .containsExactly(Type.SUSPEND, Type.REINSTATE, Type.PUBLISH, Type.ARCHIVE);
    }

    @Test
    void adminSetStatus_invalidTransitionsAndMissingReason() {
        hotel.setPublicationStatus(PublicationStatus.DRAFT);
        assertThatThrownBy(() -> service.adminSetStatus(adminId, hotelId, new AdminStatusRequest("SUSPEND", "Sikayet incelemesi")))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_STATE_TRANSITION"));
        assertThatThrownBy(() -> service.adminSetStatus(adminId, hotelId, new AdminStatusRequest("PUBLISH", null)))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_STATE_TRANSITION"));
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        assertThatThrownBy(() -> service.adminSetStatus(adminId, hotelId, new AdminStatusRequest("SUSPEND", null)))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        assertThatThrownBy(() -> service.adminSetStatus(adminId, hotelId, new AdminStatusRequest("BOGUS", "xxxxxx")))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        hotel.setPublicationStatus(PublicationStatus.ARCHIVED);
        assertThatThrownBy(() -> service.adminSetStatus(adminId, hotelId, new AdminStatusRequest("REINSTATE", "Temizlendi tamam")))
                .isInstanceOf(PublicationException.class);
    }

    @Test
    void adminSetStatus_legacyHotelWithoutOrg_allowedForAdmin() {
        hotel.setOrganizationId(null);
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        service.adminSetStatus(adminId, hotelId, new AdminStatusRequest("SUSPEND", "Sikayet incelemesi"));
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.SUSPENDED);
        assertThat(lastDecision(1, 0).getOrganizationId()).isNull();
    }

    // ---- content revision (U9)

    @Test
    void patchContent_published_createsDraftRevision_andNeverTouchesHotelRow() {
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        when(revisionRepository.findWorking(hotelId)).thenReturn(Optional.empty());
        when(revisionRepository.maxRevisionNo(hotelId)).thenReturn(2);

        RevisionView view = service.patchContent(partnerId, hotelId, new PatchContentRequest(0L, Map.of("description", "yeni", "starRating", 5)));

        assertThat(view.revisionNo()).isEqualTo(3);
        assertThat(view.status()).isEqualTo("DRAFT");
        assertThat(view.content()).containsEntry("description", "yeni").containsEntry("starRating", 5).containsEntry("name", "Grand");
        assertThat(view.baseContentVersion()).isZero();
        assertThat(hotel.getDescription()).isEqualTo("old");
        assertThat(hotel.getStarRating()).isEqualTo(4);
        verify(hotelRepository, never()).save(any(Hotel.class));
    }

    @Test
    void patchContent_staleExpectedVersion_409WithCurrentVersion() {
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        hotel.setContentVersion(2);
        when(revisionRepository.findWorking(hotelId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patchContent(partnerId, hotelId, new PatchContentRequest(1L, Map.of("description", "x"))))
                .isInstanceOfSatisfying(PublicationException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("VERSION_CONFLICT");
                    assertThat(e.getDetails()).containsEntry("currentVersion", 2L);
                });

        HotelContentRevision working = revision(HotelContentRevision.Status.DRAFT, 2);
        when(revisionRepository.findWorking(hotelId)).thenReturn(Optional.of(working));
        assertThatThrownBy(() -> service.patchContent(partnerId, hotelId, new PatchContentRequest(5L, Map.of("description", "x"))))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getCode()).isEqualTo("VERSION_CONFLICT"));
    }

    @Test
    void patchContent_existingDraft_isReusedAndMerged() {
        hotel.setPublicationStatus(PublicationStatus.SUSPENDED);
        HotelContentRevision working = revision(HotelContentRevision.Status.DRAFT, 0);
        when(revisionRepository.findWorking(hotelId)).thenReturn(Optional.of(working));

        RevisionView view = service.patchContent(partnerId, hotelId, new PatchContentRequest(0L, Map.of("city", "Izmir")));

        assertThat(view.content()).containsEntry("city", "Izmir").containsEntry("description", "new text");
        verify(revisionRepository, never()).maxRevisionNo(any());
    }

    @Test
    void patchContent_submittedRevision_locked() {
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        when(revisionRepository.findWorking(hotelId)).thenReturn(Optional.of(revision(HotelContentRevision.Status.SUBMITTED, 0)));
        assertThatThrownBy(() -> service.patchContent(partnerId, hotelId, new PatchContentRequest(0L, Map.of("city", "Izmir"))))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getCode()).isEqualTo("HOTEL_LOCKED_FOR_REVIEW"));
    }

    @Test
    void patchContent_perHotelStatus() {
        Map<PublicationStatus, String> expected = Map.of(
                PublicationStatus.DRAFT, "USE_DIRECT_EDIT", PublicationStatus.REJECTED, "USE_DIRECT_EDIT",
                PublicationStatus.SUBMITTED, "HOTEL_LOCKED_FOR_REVIEW", PublicationStatus.APPROVED, "HOTEL_LOCKED_FOR_REVIEW",
                PublicationStatus.ARCHIVED, "HOTEL_ARCHIVED");
        expected.forEach((status, code) -> {
            hotel.setPublicationStatus(status);
            assertThatThrownBy(() -> service.patchContent(partnerId, hotelId, new PatchContentRequest(0L, Map.of("city", "Izmir"))))
                    .as(status.name())
                    .isInstanceOfSatisfying(PublicationException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(409);
                        assertThat(e.getCode()).isEqualTo(code);
                    });
        });
    }

    @Test
    void patchContent_validation_unknownKeysAndRanges() {
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        for (Map<String, Object> bad : List.<Map<String, Object>>of(
                Map.of("organizationId", UUID.randomUUID().toString()),
                Map.of("active", false),
                Map.of("starRating", 6),
                Map.of("starRating", 0),
                Map.of("name", ""),
                Map.of("latitude", 91),
                Map.of("longitude", -181),
                Map.of("description", "x".repeat(5001)),
                Map.of("address", "y".repeat(501)))) {
            assertThatThrownBy(() -> service.patchContent(partnerId, hotelId, new PatchContentRequest(0L, bad)))
                    .as(bad.toString())
                    .isInstanceOfSatisfying(PublicationException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(400);
                        assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
                    });
        }
        assertThatThrownBy(() -> service.patchContent(partnerId, hotelId, new PatchContentRequest(null, Map.of("city", "x"))))
                .isInstanceOf(PublicationException.class);
        assertThatThrownBy(() -> service.patchContent(partnerId, hotelId, new PatchContentRequest(0L, Map.of())))
                .isInstanceOf(PublicationException.class);
    }

    @Test
    void patchContent_nullableFieldsAcceptNull() {
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        when(revisionRepository.findWorking(hotelId)).thenReturn(Optional.empty());
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("description", null);
        patch.put("latitude", null);
        RevisionView view = service.patchContent(partnerId, hotelId, new PatchContentRequest(0L, patch));
        assertThat(view.content().get("description")).isNull();
        assertThat(view.content().get("latitude")).isNull();
    }

    // ---- hotel level gates used by PUT/DELETE (U10)

    @Test
    void assertDirectEditable_perStatus() {
        Map<PublicationStatus, String> blocked = Map.of(
                PublicationStatus.SUBMITTED, "HOTEL_LOCKED_FOR_REVIEW", PublicationStatus.APPROVED, "HOTEL_LOCKED_FOR_REVIEW",
                PublicationStatus.PUBLISHED, "CONTENT_REQUIRES_REVISION", PublicationStatus.SUSPENDED, "CONTENT_REQUIRES_REVISION",
                PublicationStatus.ARCHIVED, "HOTEL_ARCHIVED");
        blocked.forEach((status, code) -> {
            hotel.setPublicationStatus(status);
            assertThatThrownBy(() -> service.assertDirectEditable(hotelId, orgId))
                    .as(status.name())
                    .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getCode()).isEqualTo(code));
        });
        hotel.setPublicationStatus(PublicationStatus.DRAFT);
        service.assertDirectEditable(hotelId, orgId);
        hotel.setPublicationStatus(PublicationStatus.REJECTED);
        service.assertDirectEditable(hotelId, null);
    }

    @Test
    void assertDirectEditable_organizationChange_400() {
        assertThatThrownBy(() -> service.assertDirectEditable(hotelId, UUID.randomUUID()))
                .isInstanceOfSatisfying(PublicationException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getCode()).isEqualTo("ORGANIZATION_CHANGE_NOT_ALLOWED");
                });
    }

    @Test
    void assertDeletable_onlyDraftWithoutHistory() {
        service.assertDeletable(hotelId);

        when(decisionRepository.existsByHotelId(hotelId)).thenReturn(true);
        assertThatThrownBy(() -> service.assertDeletable(hotelId))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getCode()).isEqualTo("HOTEL_HAS_HISTORY"));

        when(decisionRepository.existsByHotelId(hotelId)).thenReturn(false);
        when(requestRepository.existsByHotelId(hotelId)).thenReturn(true);
        assertThatThrownBy(() -> service.assertDeletable(hotelId)).isInstanceOf(PublicationException.class);

        when(requestRepository.existsByHotelId(hotelId)).thenReturn(false);
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        assertThatThrownBy(() -> service.assertDeletable(hotelId)).isInstanceOf(PublicationException.class);
    }

    @Test
    void diff_listsOnlyChangedFields() {
        Map<String, Object> live = HotelPublicationService.liveContent(hotel);
        Map<String, Object> rev = new LinkedHashMap<>(live);
        rev.put("description", "new text");
        rev.put("latitude", new BigDecimal("36.50"));
        List<DiffEntry> diff = HotelPublicationService.diff(live, rev);
        assertThat(diff).extracting(DiffEntry::field).containsExactly("description");
    }

    // ---- media phase 3

    private final java.util.Map<UUID, HotelMedia> photos = new java.util.LinkedHashMap<>();

    private HotelMedia photo(HotelMedia.ReviewStatus status) {
        HotelMedia m = new HotelMedia();
        ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
        m.setHotelId(hotelId);
        m.setOrganizationId(orgId);
        m.setReviewStatus(status);
        when(mediaRepository.findByIdAndHotelId(m.getId(), hotelId)).thenReturn(Optional.of(m));
        if (photos.isEmpty()) {
            // stubbed once: re-stubbing would call the previous answer with a null argument during when(...)
            when(mediaRepository.findAllById(any())).thenAnswer(i -> {
                List<HotelMedia> out = new java.util.ArrayList<>();
                Iterable<UUID> ids = i.getArgument(0);
                if (ids != null) ids.forEach(id -> { HotelMedia p = photos.get(id); if (p != null) out.add(p); });
                return out;
            });
        }
        photos.put(m.getId(), m);
        return m;
    }

    private void linked(HotelMedia... linkedPhotos) {
        when(linkRepository.findByRequestId(any())).thenReturn(java.util.Arrays.stream(linkedPhotos)
                .map(m -> new HotelPublicationRequestMedia(UUID.randomUUID(), m.getId(), hotelId, orgId)).toList());
    }

    @Test
    void submitInitial_withMediaIds_savesLinks() {
        HotelMedia a = photo(HotelMedia.ReviewStatus.PENDING);
        HotelMedia b = photo(HotelMedia.ReviewStatus.PENDING);

        service.submit(partnerId, hotelId, new SubmitRequest(null, null, null, List.of(a.getId(), b.getId())));

        ArgumentCaptor<Iterable<HotelPublicationRequestMedia>> c = ArgumentCaptor.forClass(Iterable.class);
        verify(linkRepository).saveAll(c.capture());
        assertThat(c.getValue()).extracting(HotelPublicationRequestMedia::getMediaId).containsExactly(a.getId(), b.getId());
    }

    @Test
    void submit_mediaValidation_400_404_409() {
        HotelMedia approved = photo(HotelMedia.ReviewStatus.APPROVED);
        UUID id = UUID.randomUUID();
        List<UUID> tooMany = java.util.stream.Stream.generate(UUID::randomUUID).limit(21).toList();
        List<UUID> withNull = new java.util.ArrayList<>(java.util.Arrays.asList(id, null));

        for (List<UUID> bad : List.of(tooMany, List.of(id, id), withNull)) {
            assertThatThrownBy(() -> service.submit(partnerId, hotelId, new SubmitRequest(null, null, null, bad)))
                    .isInstanceOfSatisfying(PublicationException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(400);
                        assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
                    });
        }
        assertThatThrownBy(() -> service.submit(partnerId, hotelId, new SubmitRequest(null, null, null, List.of(id))))
                .isInstanceOfSatisfying(PublicationException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(404);
                    assertThat(e.getCode()).isEqualTo("MEDIA_NOT_FOUND");
                });
        assertThatThrownBy(() -> service.submit(partnerId, hotelId, new SubmitRequest(null, null, null, List.of(approved.getId()))))
                .isInstanceOfSatisfying(PublicationException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo("MEDIA_NOT_PENDING");
                    assertThat(e.getDetails()).containsEntry("mediaId", approved.getId().toString())
                            .containsEntry("reviewStatus", "APPROVED");
                });
        verify(linkRepository, never()).saveAll(any());
    }

    @Test
    void adminApprove_cascadesPendingPhotos_skipsAlreadyRejected() {
        hotel.setPublicationStatus(PublicationStatus.SUBMITTED);
        openRequest(HotelPublicationRequest.Kind.INITIAL, null);
        HotelMedia pending = photo(HotelMedia.ReviewStatus.PENDING);
        HotelMedia rejected = photo(HotelMedia.ReviewStatus.REJECTED);
        linked(pending, rejected);

        service.adminDecide(adminId, UUID.randomUUID(), new AdminDecisionRequest("APPROVE_AND_PUBLISH", null, 0L));

        assertThat(pending.getReviewStatus()).isEqualTo(HotelMedia.ReviewStatus.APPROVED);
        assertThat(pending.getScanStatus()).isEqualTo(HotelMedia.ScanStatus.CLEAN);
        assertThat(pending.getReviewedBy()).isEqualTo(adminId);
        assertThat(pending.getReviewedAt()).isEqualTo(Instant.now(clock));
        assertThat(pending.getReviewReason()).isNull();
        assertThat(rejected.getReviewStatus()).isEqualTo(HotelMedia.ReviewStatus.REJECTED);
        ArgumentCaptor<HotelMediaDecision> c = ArgumentCaptor.forClass(HotelMediaDecision.class);
        verify(mediaDecisionRepository).save(c.capture());
        assertThat(c.getValue().getMediaId()).isEqualTo(pending.getId());
        assertThat(c.getValue().getDecision()).isEqualTo("APPROVE");
        assertThat(c.getValue().getFromStatus()).isEqualTo("PENDING");
        assertThat(c.getValue().getToStatus()).isEqualTo("APPROVED");
        assertThat(c.getValue().getActorUserId()).isEqualTo(adminId);
    }

    @Test
    void adminReject_leavesLinkedPhotosPending() {
        hotel.setPublicationStatus(PublicationStatus.SUBMITTED);
        openRequest(HotelPublicationRequest.Kind.INITIAL, null);
        HotelMedia pending = photo(HotelMedia.ReviewStatus.PENDING);
        linked(pending);

        service.adminDecide(adminId, UUID.randomUUID(), new AdminDecisionRequest("REJECT", "Adres eksik", 0L));

        assertThat(pending.getReviewStatus()).isEqualTo(HotelMedia.ReviewStatus.PENDING);
        verify(mediaDecisionRepository, never()).save(any());
    }

    @Test
    void adminApprove_contentChange_cascades() {
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        HotelContentRevision rev = revision(HotelContentRevision.Status.SUBMITTED, 0);
        openRequest(HotelPublicationRequest.Kind.CONTENT_CHANGE, UUID.randomUUID());
        when(revisionRepository.findById(any())).thenReturn(Optional.of(rev));
        HotelMedia pending = photo(HotelMedia.ReviewStatus.PENDING);
        linked(pending);

        service.adminDecide(adminId, UUID.randomUUID(), new AdminDecisionRequest("APPROVE", null, 0L));

        assertThat(pending.getReviewStatus()).isEqualTo(HotelMedia.ReviewStatus.APPROVED);
        verify(mediaDecisionRepository).save(any(HotelMediaDecision.class));
    }
}
