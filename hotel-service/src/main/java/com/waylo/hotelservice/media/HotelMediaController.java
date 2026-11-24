package com.waylo.hotelservice.media;

import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.PublicationStatus;
import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.media.MediaDtos.*;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.security.HotelAccessGuard;
import com.waylo.hotelservice.service.HotelService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
public class HotelMediaController {

    private final HotelMediaService service;
    private final HotelAccessGuard guard;
    private final HotelService hotelService;
    private final HotelRepository hotels;

    public HotelMediaController(HotelMediaService service, HotelAccessGuard guard, HotelService hotelService,
                                HotelRepository hotels) {
        this.service = service;
        this.guard = guard;
        this.hotelService = hotelService;
        this.hotels = hotels;
    }

    // ---- partner (guard first, then the service transaction)

    @PostMapping(value = "/api/hotels/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaView>> upload(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String caption,
            @RequestParam(required = false) String rightsSource,
            @RequestParam(required = false) String rightsNote) {
        guard.requireHotelManage(userId, id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(service.upload(userId, id, file, caption, rightsSource, rightsNote), "Photo uploaded"));
    }

    @GetMapping("/api/hotels/{id}/media/mine")
    public ResponseEntity<ApiResponse<List<MediaView>>> listOwn(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId, @PathVariable UUID id) {
        guard.requireHotelManage(userId, id);
        return ResponseEntity.ok(ApiResponse.of(service.listOwn(id)));
    }

    @PutMapping("/api/hotels/{id}/media/order")
    public ResponseEntity<ApiResponse<List<MediaView>>> reorder(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId, @PathVariable UUID id,
            @RequestBody OrderRequest body) {
        guard.requireHotelManage(userId, id);
        return ResponseEntity.ok(ApiResponse.of(service.reorder(id, body.ids())));
    }

    @PatchMapping("/api/hotels/{id}/media/{mediaId}")
    public ResponseEntity<ApiResponse<MediaView>> caption(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId, @PathVariable UUID id,
            @PathVariable UUID mediaId, @RequestBody CaptionRequest body) {
        guard.requireHotelManage(userId, id);
        return ResponseEntity.ok(ApiResponse.of(service.setCaption(id, mediaId, body.caption())));
    }

    @DeleteMapping("/api/hotels/{id}/media/{mediaId}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId, @PathVariable UUID id,
            @PathVariable UUID mediaId) {
        guard.requireHotelManage(userId, id);
        boolean published = hotels.findById(id).map(h -> h.getPublicationStatus() == PublicationStatus.PUBLISHED).orElse(false);
        service.delete(id, mediaId, published);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/hotels/{id}/media/{mediaId}/file")
    public ResponseEntity<byte[]> ownFile(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId, @PathVariable UUID id,
            @PathVariable UUID mediaId) {
        guard.requireHotelManage(userId, id);
        Hotel hotel = hotels.findById(id).orElseThrow(() -> new ResourceNotFoundException("Hotel", "id", id));
        StoredFile f = service.ownFile(id, hotel.getOrganizationId(), mediaId);
        return image(f, CacheControl.noStore().cachePrivate());
    }

    // ---- public

    @GetMapping("/api/hotels/{id}/media")
    public ResponseEntity<ApiResponse<List<PublicMedia>>> publicList(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(service.publicList(id, isPublic(id))));
    }

    @GetMapping("/api/hotels/{id}/media/{mediaId}")
    public ResponseEntity<byte[]> publicFile(@PathVariable UUID id, @PathVariable UUID mediaId) {
        StoredFile f = service.publicFile(id, mediaId, isPublic(id));
        return image(f, CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic());
    }

    private boolean isPublic(UUID id) {
        try {
            return hotelService.isPublic(id);
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    private static ResponseEntity<byte[]> image(StoredFile f, CacheControl cache) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType()))
                .cacheControl(cache).header("X-Content-Type-Options", "nosniff").body(f.content());
    }

    // ---- admin

    @GetMapping("/api/admin/hotel-media")
    public ResponseEntity<ApiResponse<AdminMediaPage>> adminList(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false, defaultValue = "PENDING") String reviewStatus,
            @RequestParam(required = false) UUID hotelId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        AdminHotelMediaGate.requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.of(service.adminList(reviewStatus, hotelId, page, size)));
    }

    @GetMapping("/api/admin/hotel-media/{mediaId}/file")
    public ResponseEntity<byte[]> adminFile(@RequestHeader(value = "X-User-Role", required = false) String role,
                                            @PathVariable UUID mediaId) {
        AdminHotelMediaGate.requireAdmin(role);
        return image(service.adminFile(mediaId), CacheControl.noStore().cachePrivate());
    }

    @PostMapping("/api/admin/hotel-media/{mediaId}/decisions")
    public ResponseEntity<ApiResponse<AdminMediaView>> review(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID mediaId, @RequestBody ReviewRequest body) {
        AdminHotelMediaGate.requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.of(service.review(userId, mediaId, body.decision(), body.reason()), "Decision recorded"));
    }
}
