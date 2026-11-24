package com.waylo.crmservice.dto;

import com.waylo.crmservice.domain.SupportCase;
import com.waylo.crmservice.domain.SupportMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SupportDtos {
    private SupportDtos() {}

    public record OpenCaseRequest(@NotNull UUID orderId, UUID orderItemId,
                                  @NotBlank @Size(max = 150) String subject,
                                  @NotBlank @Size(max = 4000) String message) {}

    public record MessageRequest(@NotBlank @Size(max = 4000) String body) {}

    public record MessageResponse(UUID id, SupportMessage.Role authorRole, UUID authorUserId, String body, Instant createdAt) {
        public static MessageResponse of(SupportMessage m) {
            return new MessageResponse(m.getId(), m.getAuthorRole(), m.getAuthorUserId(), m.getBody(), m.getCreatedAt());
        }
    }

    /** messages is null in list views. */
    public record CaseResponse(UUID id, UUID customerId, UUID orderId, UUID orderItemId, UUID supplierOrganizationId,
                               String subject, SupportCase.Status status, Instant createdAt, Instant updatedAt,
                               List<MessageResponse> messages) {
        public static CaseResponse of(SupportCase c, List<SupportMessage> messages) {
            return new CaseResponse(c.getId(), c.getCustomerId(), c.getOrderId(), c.getOrderItemId(), c.getSupplierOrganizationId(),
                    c.getSubject(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt(),
                    messages == null ? null : messages.stream().map(MessageResponse::of).toList());
        }
    }

    /** Honest channel capability (RM-36 / RM-06): no live chat, no channels and no hours until the operations playbook is decided. */
    public record Capabilities(boolean liveChat, List<String> channels, String hours, boolean decisionPending) {
        public static Capabilities current() {
            return new Capabilities(false, List.of(), null, true);
        }
    }
}
