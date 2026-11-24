package com.waylo.hotelservice.publication;

import com.waylo.hotelservice.dto.HotelResponse;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request and response shapes of contract sec. 4. */
public final class PublicationDtos {

    private PublicationDtos() {
    }

    public record SubmitRequest(@Size(max = 1000) String note, UUID revisionId, Long expectedVersion) {}

    public record PatchContentRequest(Long expectedVersion, Map<String, Object> content) {}

    public record ReasonRequest(String reason) {}

    public record AdminDecisionRequest(String decision, String reason, Long expectedVersion) {}

    public record AdminStatusRequest(String decision, String reason) {}

    public record RequestView(UUID id, UUID hotelId, UUID organizationId, String kind, String status, UUID revisionId,
                              UUID submittedBy, Instant submittedAt, String note, long version,
                              String hotelPublicationStatus) {}

    public record DecisionView(String decision, String reason, String actorRole, Instant createdAt) {}

    public record PartnerRequestItem(UUID id, String kind, String status, Instant submittedAt, Instant decidedAt,
                                     DecisionView decision) {}

    public record RevisionView(UUID id, int revisionNo, String status, long version, long baseContentVersion,
                               Map<String, Object> content) {}

    public record ContentView(UUID hotelId, String publicationStatus, long contentVersion, Map<String, Object> live,
                              RevisionView revision) {}

    public record AdminItem(UUID requestId, UUID hotelId, String hotelName, UUID organizationId, String kind,
                            String status, UUID submittedBy, Instant submittedAt, String note,
                            String hotelPublicationStatus, long version) {}

    public record AdminPage(List<AdminItem> items, int page, int size, long total) {}

    public record DiffEntry(String field, Object from, Object to) {}

    public record AdminDecisionEntry(UUID id, UUID requestId, String decision, String fromStatus, String toStatus,
                                     UUID actorUserId, String actorRole, String reason, Instant createdAt) {}

    public record AdminDetail(RequestView request, HotelResponse hotel, RevisionView revision, List<DiffEntry> diff,
                              List<AdminDecisionEntry> decisions) {}
}
