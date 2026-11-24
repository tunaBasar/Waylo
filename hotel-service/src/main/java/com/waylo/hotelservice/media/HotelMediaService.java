package com.waylo.hotelservice.media;

import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.exception.PublicationException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.media.MediaDtos.*;
import com.waylo.hotelservice.repository.HotelRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hotel photos (contract sec. 5). Callers must run HotelAccessGuard before any method here that takes a hotel the
 * partner manages, so the transaction starts in RLS partner mode.
 */
@Service
public class HotelMediaService {

    private static final Set<String> RIGHTS = Set.of("OWNED", "LICENSED", "PARTNER_ATTESTED");

    private final HotelMediaRepository media;
    private final HotelMediaDecisionRepository decisions;
    private final HotelRepository hotels;
    private final Path baseDir;

    public HotelMediaService(HotelMediaRepository media, HotelMediaDecisionRepository decisions, HotelRepository hotels,
                             @Value("${HOTEL_MEDIA_DIR:/data/hotel-media}") String baseDir) {
        this.media = media;
        this.decisions = decisions;
        this.hotels = hotels;
        this.baseDir = Path.of(baseDir);
    }

    // ---- partner

    @Transactional
    public MediaView upload(UUID userId, UUID hotelId, MultipartFile file, String caption, String rightsSource, String rightsNote) {
        if (file == null || file.isEmpty()) {
            throw new PublicationException(400, "EMPTY_FILE", "Uploaded file is empty");
        }
        if (file.getSize() > MediaFiles.MAX_BYTES) {
            throw new PublicationException(413, "FILE_TOO_LARGE", "Photo exceeds the 5 MB limit");
        }
        String rights = rightsSource == null || rightsSource.isBlank() ? "PARTNER_ATTESTED" : rightsSource;
        if (!RIGHTS.contains(rights)) {
            throw PublicationException.validation("rightsSource must be OWNED, LICENSED or PARTNER_ATTESTED");
        }
        String cleanCaption = cleanCaption(caption);
        if (rightsNote != null && rightsNote.length() > 500) {
            throw PublicationException.validation("rightsNote must be at most 500 characters");
        }
        Hotel hotel = hotels.findById(hotelId).orElseThrow(() -> new ResourceNotFoundException("Hotel", "id", hotelId));
        if (hotel.getOrganizationId() == null) {
            throw new PublicationException(409, "HOTEL_NOT_OWNED", "Hotel has no owning organization");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        MediaFiles.Inspected img = MediaFiles.inspect(content);
        enforceCount(media.countByHotelId(hotelId));

        UUID key = UUID.randomUUID();
        HotelMedia m = new HotelMedia();
        m.setHotelId(hotelId);
        m.setOrganizationId(hotel.getOrganizationId());
        m.setCaption(cleanCaption);
        m.setRightsSource(rights);
        m.setRightsNote(rightsNote == null || rightsNote.isBlank() ? null : rightsNote.trim());
        m.setStorageKey(key);
        m.setContentType(img.contentType());
        m.setSizeBytes(img.cleaned().length);
        m.setWidth(img.width());
        m.setHeight(img.height());
        // No external scanner exists: the structural decode above is the scan. Review is the admin step.
        m.setScanStatus(HotelMedia.ScanStatus.CLEAN);
        m.setReviewStatus(HotelMedia.ReviewStatus.PENDING);
        m.setUploadedBy(userId);
        m.setSortOrder((int) media.countByHotelId(hotelId));
        try {
            Files.createDirectories(baseDir);
            Files.write(baseDir.resolve(key.toString()), img.cleaned());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            return MediaView.of(media.saveAndFlush(m));
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
    }

    static void enforceCount(long existing) {
        if (existing >= MediaFiles.MAX_PER_HOTEL) {
            throw PublicationException.conflict("MEDIA_LIMIT_REACHED",
                    "A hotel can have at most " + MediaFiles.MAX_PER_HOTEL + " photos");
        }
    }

    @Transactional(readOnly = true)
    public List<MediaView> listOwn(UUID hotelId) {
        return media.findByHotelIdOrderBySortOrderAscCreatedAtAsc(hotelId).stream().map(MediaView::of).toList();
    }

    @Transactional
    public MediaView setCaption(UUID hotelId, UUID mediaId, String caption) {
        HotelMedia m = own(hotelId, mediaId);
        m.setCaption(cleanCaption(caption));
        // A live photo whose text changed must be reviewed again. The DB trigger lets a partner only go back to PENDING.
        if (m.getReviewStatus() != HotelMedia.ReviewStatus.PENDING) {
            m.setReviewStatus(HotelMedia.ReviewStatus.PENDING);
            m.setReviewReason(null);
        }
        return MediaView.of(media.saveAndFlush(m));
    }

    @Transactional
    public List<MediaView> reorder(UUID hotelId, List<UUID> ids) {
        List<HotelMedia> all = media.findByHotelIdOrderBySortOrderAscCreatedAtAsc(hotelId);
        Set<UUID> existing = all.stream().map(HotelMedia::getId).collect(Collectors.toSet());
        if (ids == null || ids.size() != existing.size() || !existing.equals(new HashSet<>(ids))) {
            throw PublicationException.validation("ids must list every photo of the hotel exactly once");
        }
        Map<UUID, HotelMedia> byId = all.stream().collect(Collectors.toMap(HotelMedia::getId, Function.identity()));
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).setSortOrder(i);
        }
        media.saveAllAndFlush(all);
        return listOwn(hotelId);
    }

    @Transactional
    public void delete(UUID hotelId, UUID mediaId, boolean hotelPublished) {
        HotelMedia m = own(hotelId, mediaId);
        if (hotelPublished && m.getReviewStatus() == HotelMedia.ReviewStatus.APPROVED) {
            throw PublicationException.conflict("MEDIA_LIVE", "An approved photo of a published hotel cannot be deleted");
        }
        // Soft delete: the append-only review decisions keep pointing at this row (V12). The file itself is removed.
        m.setDeletedAt(java.time.Instant.now());
        media.saveAndFlush(m);
        deleteQuietly(m.getStorageKey());
    }

    @Transactional(readOnly = true)
    public StoredFile ownFile(UUID hotelId, UUID organizationId, UUID mediaId) {
        HotelMedia m = media.findByIdAndHotelIdAndOrganizationId(mediaId, hotelId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Media", "id", mediaId));
        return read(m);
    }

    // ---- public

    @Transactional(readOnly = true)
    public List<PublicMedia> publicList(UUID hotelId, boolean hotelPublic) {
        if (!hotelPublic) return List.of();
        return media.findByHotelIdAndScanStatusAndReviewStatusOrderBySortOrderAscCreatedAtAsc(
                hotelId, HotelMedia.ScanStatus.CLEAN, HotelMedia.ReviewStatus.APPROVED).stream().map(PublicMedia::of).toList();
    }

    @Transactional(readOnly = true)
    public StoredFile publicFile(UUID hotelId, UUID mediaId, boolean hotelPublic) {
        HotelMedia m = media.findByIdAndHotelId(mediaId, hotelId).orElse(null);
        if (m == null || !HotelMediaRules.isPubliclyServable(m.getScanStatus(), m.getReviewStatus(), hotelPublic)) {
            throw new ResourceNotFoundException("Media", "id", mediaId);
        }
        return read(m);
    }

    // ---- admin

    @Transactional(readOnly = true)
    public AdminMediaPage adminList(String reviewStatus, UUID hotelId, int page, int size) {
        HotelMedia.ReviewStatus status = null;
        if (reviewStatus != null && !reviewStatus.isBlank()) {
            try {
                status = HotelMedia.ReviewStatus.valueOf(reviewStatus);
            } catch (IllegalArgumentException e) {
                throw PublicationException.validation("reviewStatus must be PENDING, APPROVED or REJECTED");
            }
        }
        final HotelMedia.ReviewStatus st = status;
        Specification<HotelMedia> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (st != null) p.add(cb.equal(root.get("reviewStatus"), st));
            if (hotelId != null) p.add(cb.equal(root.get("hotelId"), hotelId));
            return cb.and(p.toArray(new Predicate[0]));
        };
        var result = media.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("createdAt").ascending()));
        Map<UUID, String> names = hotels.findAllById(result.getContent().stream().map(HotelMedia::getHotelId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Hotel::getId, Hotel::getName));
        List<AdminMediaView> items = result.getContent().stream().map(m -> adminView(m, names.get(m.getHotelId()))).toList();
        return new AdminMediaPage(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public StoredFile adminFile(UUID mediaId) {
        return read(media.findById(mediaId).orElseThrow(() -> new ResourceNotFoundException("Media", "id", mediaId)));
    }

    @Transactional
    public AdminMediaView review(UUID adminId, UUID mediaId, String decision, String reason) {
        if (adminId == null) {
            throw new PublicationException(401, "UNAUTHORIZED", "Authentication required");
        }
        HotelMedia m = media.findById(mediaId).orElseThrow(() -> new ResourceNotFoundException("Media", "id", mediaId));
        HotelMedia.ReviewStatus target;
        if ("APPROVE".equals(decision)) {
            target = HotelMedia.ReviewStatus.APPROVED;
        } else if ("REJECT".equals(decision)) {
            target = HotelMedia.ReviewStatus.REJECTED;
            if (reason == null || reason.trim().length() < 5) {
                throw PublicationException.validation("reason is required (at least 5 characters) to reject a photo");
            }
        } else {
            throw PublicationException.validation("decision must be APPROVE or REJECT");
        }
        if (m.getReviewStatus() == target) {
            throw PublicationException.conflict("ALREADY_DECIDED", "Photo is already " + target);
        }
        String from = m.getReviewStatus().name();
        m.setReviewStatus(target);
        m.setReviewReason(target == HotelMedia.ReviewStatus.REJECTED ? reason.trim() : null);
        m.setReviewedBy(adminId);
        m.setReviewedAt(java.time.Instant.now());
        if (target == HotelMedia.ReviewStatus.APPROVED) {
            m.setScanStatus(HotelMedia.ScanStatus.CLEAN);
        }
        media.saveAndFlush(m);
        decisions.save(new HotelMediaDecision(m, decision, from, target.name(),
                target == HotelMedia.ReviewStatus.REJECTED ? reason.trim() : null, adminId));
        String name = hotels.findById(m.getHotelId()).map(Hotel::getName).orElse(null);
        return adminView(m, name);
    }

    // ---- helpers

    private AdminMediaView adminView(HotelMedia m, String hotelName) {
        List<DecisionView> history = decisions.findByMediaIdOrderByCreatedAtDesc(m.getId()).stream().map(DecisionView::of).toList();
        return new AdminMediaView(MediaView.of(m), hotelName, m.getOrganizationId(), m.getUploadedBy(),
                m.getReviewedBy(), m.getReviewedAt(), history);
    }

    private HotelMedia own(UUID hotelId, UUID mediaId) {
        return media.findByIdAndHotelId(mediaId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Media", "id", mediaId));
    }

    private static String cleanCaption(String caption) {
        if (caption == null || caption.isBlank()) return null;
        String c = caption.trim();
        if (c.length() > 200) {
            throw PublicationException.validation("caption must be at most 200 characters");
        }
        return c;
    }

    private StoredFile read(HotelMedia m) {
        try {
            return new StoredFile(Files.readAllBytes(baseDir.resolve(m.getStorageKey().toString())), m.getContentType());
        } catch (NoSuchFileException e) {
            throw new ResourceNotFoundException("Media", "id", m.getId());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deleteQuietly(UUID key) {
        try {
            Files.deleteIfExists(baseDir.resolve(key.toString()));
        } catch (IOException ignored) {
            // orphan file is harmless: random name, never referenced
        }
    }
}
