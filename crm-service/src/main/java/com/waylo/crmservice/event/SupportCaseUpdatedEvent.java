package com.waylo.crmservice.event;

import java.util.UUID;

/** support-events, token supportCaseUpdated. Carries no message text on purpose. */
public class SupportCaseUpdatedEvent extends BaseEvent {
    public enum Kind { ANSWERED, CLOSED }
    public enum AuthorRole { PARTNER, ADMIN }

    private UUID caseId;
    private UUID customerId;
    private UUID orderId;
    private String subject;
    private Kind kind;
    private AuthorRole authorRole;

    public SupportCaseUpdatedEvent() { super("SUPPORT_CASE_UPDATED", "crm-service"); }

    public SupportCaseUpdatedEvent(UUID caseId, UUID customerId, UUID orderId, String subject, Kind kind, AuthorRole authorRole) {
        this();
        this.caseId = caseId;
        this.customerId = customerId;
        this.orderId = orderId;
        this.subject = subject;
        this.kind = kind;
        this.authorRole = authorRole;
    }

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public AuthorRole getAuthorRole() { return authorRole; }
    public void setAuthorRole(AuthorRole authorRole) { this.authorRole = authorRole; }
}
