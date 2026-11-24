package com.waylo.hotelservice.media;

import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.exception.PublicationException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.media.MediaDtos.StoredFile;
import com.waylo.hotelservice.repository.HotelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HotelMediaServiceTest {

    @TempDir Path dir;
    HotelMediaRepository media = mock(HotelMediaRepository.class);
    HotelMediaDecisionRepository decisions = mock(HotelMediaDecisionRepository.class);
    HotelRepository hotels = mock(HotelRepository.class);
    HotelMediaService service;

    final UUID hotelId = UUID.randomUUID();
    final UUID orgId = UUID.randomUUID();
    final UUID user = UUID.randomUUID();
    final UUID admin = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new HotelMediaService(media, decisions, hotels, dir.toString());
        Hotel hotel = mock(Hotel.class);
        when(hotel.getOrganizationId()).thenReturn(orgId);
        when(hotel.getId()).thenReturn(hotelId);
        when(hotels.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(media.saveAndFlush(any(HotelMedia.class))).thenAnswer(i -> i.getArgument(0));
        when(media.countByHotelId(hotelId)).thenReturn(0L);
    }

    private MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "text/plain", bytes);
    }

    private HotelMedia stored(HotelMedia.ScanStatus scan, HotelMedia.ReviewStatus review) {
        HotelMedia m = new HotelMedia();
        m.setHotelId(hotelId);
        m.setOrganizationId(orgId);
        m.setStorageKey(UUID.randomUUID());
        m.setContentType("image/png");
        m.setScanStatus(scan);
        m.setReviewStatus(review);
        return m;
    }

    // ---- upload

    @Test
    void uploadStoresUnderRandomKeyNeverUserFilename_andStartsPending() throws Exception {
        var view = service.upload(user, hotelId, file("../../etc/passwd.php", TestImages.png(900, 700, false)),
                " Lobi ", null, null);
        ArgumentCaptor<HotelMedia> saved = ArgumentCaptor.forClass(HotelMedia.class);
        verify(media).saveAndFlush(saved.capture());
        HotelMedia m = saved.getValue();
        assertThat(m.getReviewStatus()).isEqualTo(HotelMedia.ReviewStatus.PENDING);
        assertThat(m.getScanStatus()).isEqualTo(HotelMedia.ScanStatus.CLEAN);
        assertThat(m.getCaption()).isEqualTo("Lobi");
        assertThat(m.getRightsSource()).isEqualTo("PARTNER_ATTESTED");
        assertThat(m.getOrganizationId()).isEqualTo(orgId);
        assertThat(view.reviewStatus()).isEqualTo("PENDING");
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> all = files.toList();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getFileName().toString()).isEqualTo(m.getStorageKey().toString());
            assertThat(all.get(0).getFileName().toString()).doesNotContain("passwd").doesNotContain(".php");
        }
    }

    @Test
    void fakeExtensionIsRejectedAndNothingIsWritten() throws Exception {
        assertThatThrownBy(() -> service.upload(user, hotelId, file("photo.jpg", "%PDF-1.4".getBytes()), null, null, null))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getStatus()).isEqualTo(415));
        verify(media, never()).saveAndFlush(any());
        try (Stream<Path> files = Files.list(dir)) {
            assertThat(files.count()).isZero();
        }
    }

    @Test
    void twentyPhotosIsTheLimit() {
        when(media.countByHotelId(hotelId)).thenReturn(19L);
        assertThat(service.upload(user, hotelId, file("a.png", TestImages.png(900, 700, false)), null, null, null)).isNotNull();

        when(media.countByHotelId(hotelId)).thenReturn(20L);
        assertThatThrownBy(() -> service.upload(user, hotelId, file("b.png", TestImages.png(900, 700, false)), null, null, null))
                .isInstanceOfSatisfying(PublicationException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo("MEDIA_LIMIT_REACHED");
                });
    }

    @Test
    void sizeLimitIsEnforcedBeforeReadingTheBytes() {
        var big = new MockMultipartFile("file", "big.png", "image/png", new byte[(int) MediaFiles.MAX_BYTES + 1]);
        assertThatThrownBy(() -> service.upload(user, hotelId, big, null, null, null))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getStatus()).isEqualTo(413));
    }

    @Test
    void rejectsBadRightsSourceAndLongCaption() {
        byte[] ok = TestImages.png(900, 700, false);
        assertThatThrownBy(() -> service.upload(user, hotelId, file("a.png", ok), null, "STOLEN", null))
                .isInstanceOf(PublicationException.class);
        assertThatThrownBy(() -> service.upload(user, hotelId, file("a.png", ok), "x".repeat(201), null, null))
                .isInstanceOf(PublicationException.class);
    }

    @Test
    void legacyHotelWithoutOrganizationCannotGetPhotos() {
        UUID legacy = UUID.randomUUID();
        Hotel h = mock(Hotel.class);
        when(h.getOrganizationId()).thenReturn(null);
        when(hotels.findById(legacy)).thenReturn(Optional.of(h));
        assertThatThrownBy(() -> service.upload(user, legacy, file("a.png", TestImages.png(900, 700, false)), null, null, null))
                .isInstanceOf(PublicationException.class);
    }

    // ---- public serving matrix: scan x review x hotel public

    @Test
    void servingRulesMatrix() throws Exception {
        for (HotelMedia.ScanStatus scan : HotelMedia.ScanStatus.values()) {
            for (HotelMedia.ReviewStatus review : HotelMedia.ReviewStatus.values()) {
                for (boolean hotelPublic : new boolean[] {true, false}) {
                    HotelMedia m = stored(scan, review);
                    UUID mediaId = UUID.randomUUID();
                    Files.write(dir.resolve(m.getStorageKey().toString()), new byte[] {1, 2, 3});
                    when(media.findByIdAndHotelId(mediaId, hotelId)).thenReturn(Optional.of(m));
                    boolean expected = scan == HotelMedia.ScanStatus.CLEAN && review == HotelMedia.ReviewStatus.APPROVED && hotelPublic;
                    String label = scan + "/" + review + "/public=" + hotelPublic;
                    assertThat(HotelMediaRules.isPubliclyServable(scan, review, hotelPublic)).as(label).isEqualTo(expected);
                    if (expected) {
                        StoredFile f = service.publicFile(hotelId, mediaId, hotelPublic);
                        assertThat(f.content()).as(label).hasSize(3);
                        assertThat(f.contentType()).isEqualTo("image/png");
                    } else {
                        assertThatThrownBy(() -> service.publicFile(hotelId, mediaId, hotelPublic)).as(label)
                                .isInstanceOf(ResourceNotFoundException.class);
                    }
                }
            }
        }
    }

    @Test
    void unknownMediaOrWrongHotelIs404() {
        UUID mediaId = UUID.randomUUID();
        when(media.findByIdAndHotelId(mediaId, hotelId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.publicFile(hotelId, mediaId, true)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approvedButFileMissingOnDiskIs404() {
        HotelMedia m = stored(HotelMedia.ScanStatus.CLEAN, HotelMedia.ReviewStatus.APPROVED);
        UUID mediaId = UUID.randomUUID();
        when(media.findByIdAndHotelId(mediaId, hotelId)).thenReturn(Optional.of(m));
        assertThatThrownBy(() -> service.publicFile(hotelId, mediaId, true)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void publicListIsEmptyForUnpublishedHotelAndQueriesOnlyCleanApproved() {
        assertThat(service.publicList(hotelId, false)).isEmpty();
        verify(media, never()).findByHotelIdAndScanStatusAndReviewStatusOrderBySortOrderAscCreatedAtAsc(any(), any(), any());

        HotelMedia m = stored(HotelMedia.ScanStatus.CLEAN, HotelMedia.ReviewStatus.APPROVED);
        m.setCaption("Havuz");
        when(media.findByHotelIdAndScanStatusAndReviewStatusOrderBySortOrderAscCreatedAtAsc(
                hotelId, HotelMedia.ScanStatus.CLEAN, HotelMedia.ReviewStatus.APPROVED)).thenReturn(List.of(m));
        var list = service.publicList(hotelId, true);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).caption()).isEqualTo("Havuz");
    }

    @Test
    void ownFileUsesExactObjectCheckWithOrganization() {
        UUID mediaId = UUID.randomUUID();
        UUID otherOrg = UUID.randomUUID();
        when(media.findByIdAndHotelIdAndOrganizationId(mediaId, hotelId, otherOrg)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.ownFile(hotelId, otherOrg, mediaId)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- partner ops

    @Test
    void reorderRequiresEveryPhotoExactlyOnce() {
        HotelMedia a = stored(HotelMedia.ScanStatus.CLEAN, HotelMedia.ReviewStatus.PENDING);
        HotelMedia b = stored(HotelMedia.ScanStatus.CLEAN, HotelMedia.ReviewStatus.PENDING);
        UUID ia = UUID.randomUUID(), ib = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(a, "id", ia);
        org.springframework.test.util.ReflectionTestUtils.setField(b, "id", ib);
        when(media.findByHotelIdOrderBySortOrderAscCreatedAtAsc(hotelId)).thenReturn(List.of(a, b));

        assertThatThrownBy(() -> service.reorder(hotelId, List.of(ia))).isInstanceOf(PublicationException.class);
        assertThatThrownBy(() -> service.reorder(hotelId, List.of(ia, ia))).isInstanceOf(PublicationException.class);
        assertThatThrownBy(() -> service.reorder(hotelId, List.of(ia, UUID.randomUUID()))).isInstanceOf(PublicationException.class);

        service.reorder(hotelId, List.of(ib, ia));
        assertThat(b.getSortOrder()).isZero();
        assertThat(a.getSortOrder()).isEqualTo(1);
    }

    @Test
    void liveApprovedPhotoOfPublishedHotelCannotBeDeleted_butOthersCan() throws Exception {
        UUID mediaId = UUID.randomUUID();
        HotelMedia approved = stored(HotelMedia.ScanStatus.CLEAN, HotelMedia.ReviewStatus.APPROVED);
        when(media.findByIdAndHotelId(mediaId, hotelId)).thenReturn(Optional.of(approved));
        assertThatThrownBy(() -> service.delete(hotelId, mediaId, true))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getCode()).isEqualTo("MEDIA_LIVE"));
        verify(media, never()).delete(any(HotelMedia.class));

        service.delete(hotelId, mediaId, false);
        verify(media).delete(approved);

        HotelMedia rejected = stored(HotelMedia.ScanStatus.CLEAN, HotelMedia.ReviewStatus.REJECTED);
        Files.write(dir.resolve(rejected.getStorageKey().toString()), new byte[] {1});
        when(media.findByIdAndHotelId(mediaId, hotelId)).thenReturn(Optional.of(rejected));
        service.delete(hotelId, mediaId, true);
        assertThat(Files.exists(dir.resolve(rejected.getStorageKey().toString()))).isFalse();
    }

    @Test
    void captionEditOnApprovedPhotoSendsItBackToReview() {
        UUID mediaId = UUID.randomUUID();
        HotelMedia m = stored(HotelMedia.ScanStatus.CLEAN, HotelMedia.ReviewStatus.APPROVED);
        when(media.findByIdAndHotelId(mediaId, hotelId)).thenReturn(Optional.of(m));
        service.setCaption(hotelId, mediaId, "Yeni");
        assertThat(m.getReviewStatus()).isEqualTo(HotelMedia.ReviewStatus.PENDING);
        assertThat(m.getCaption()).isEqualTo("Yeni");
    }

    // ---- admin review + audit

    @Test
    void approveWritesAuditRow() {
        UUID mediaId = UUID.randomUUID();
        HotelMedia m = stored(HotelMedia.ScanStatus.CLEAN, HotelMedia.ReviewStatus.PENDING);
        when(media.findById(mediaId)).thenReturn(Optional.of(m));
        when(decisions.findByMediaIdOrderByCreatedAtDesc(any())).thenReturn(List.of());

        service.review(admin, mediaId, "APPROVE", null);

        assertThat(m.getReviewStatus()).isEqualTo(HotelMedia.ReviewStatus.APPROVED);
        assertThat(m.getReviewedBy()).isEqualTo(admin);
        assertThat(m.getReviewedAt()).isNotNull();
        ArgumentCaptor<HotelMediaDecision> d = ArgumentCaptor.forClass(HotelMediaDecision.class);
        verify(decisions).save(d.capture());
        assertThat(d.getValue().getDecision()).isEqualTo("APPROVE");
        assertThat(d.getValue().getFromStatus()).isEqualTo("PENDING");
        assertThat(d.getValue().getToStatus()).isEqualTo("APPROVED");
        assertThat(d.getValue().getActorUserId()).isEqualTo(admin);
        assertThat(d.getValue().getOrganizationId()).isEqualTo(orgId);
    }

    @Test
    void rejectNeedsReason_andStoresIt() {
        UUID mediaId = UUID.randomUUID();
        HotelMedia m = stored(HotelMedia.ScanStatus.CLEAN, HotelMedia.ReviewStatus.PENDING);
        when(media.findById(mediaId)).thenReturn(Optional.of(m));
        when(decisions.findByMediaIdOrderByCreatedAtDesc(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.review(admin, mediaId, "REJECT", "abc")).isInstanceOf(PublicationException.class);
        assertThatThrownBy(() -> service.review(admin, mediaId, "REJECT", null)).isInstanceOf(PublicationException.class);
        verify(decisions, never()).save(any());

        service.review(admin, mediaId, "REJECT", "  Bulanik fotograf  ");
        assertThat(m.getReviewStatus()).isEqualTo(HotelMedia.ReviewStatus.REJECTED);
        assertThat(m.getReviewReason()).isEqualTo("Bulanik fotograf");
        ArgumentCaptor<HotelMediaDecision> d = ArgumentCaptor.forClass(HotelMediaDecision.class);
        verify(decisions).save(d.capture());
        assertThat(d.getValue().getReason()).isEqualTo("Bulanik fotograf");
    }

    @Test
    void repeatingTheSameDecisionConflictsAndWritesNoSecondAuditRow() {
        UUID mediaId = UUID.randomUUID();
        HotelMedia m = stored(HotelMedia.ScanStatus.CLEAN, HotelMedia.ReviewStatus.APPROVED);
        when(media.findById(mediaId)).thenReturn(Optional.of(m));
        assertThatThrownBy(() -> service.review(admin, mediaId, "APPROVE", null))
                .isInstanceOfSatisfying(PublicationException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
        verify(decisions, never()).save(any());
    }

    @Test
    void unknownDecisionAndMissingAdminAreRefused() {
        UUID mediaId = UUID.randomUUID();
        when(media.findById(mediaId)).thenReturn(Optional.of(stored(HotelMedia.ScanStatus.CLEAN, HotelMedia.ReviewStatus.PENDING)));
        assertThatThrownBy(() -> service.review(admin, mediaId, "MAYBE", null)).isInstanceOf(PublicationException.class);
        assertThatThrownBy(() -> service.review(null, mediaId, "APPROVE", null)).isInstanceOf(PublicationException.class);
        verify(decisions, never()).save(any());
    }
}
