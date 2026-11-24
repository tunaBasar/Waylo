package com.waylo.notificationservice.event;

import java.util.UUID;

/** support-events (RM-36). Carries no reply text on purpose. kind: ANSWERED | CLOSED, authorRole: PARTNER | ADMIN. */
public class SupportCaseUpdatedEvent extends BaseEvent {
    private UUID caseId;
    private UUID customerId;
    private UUID orderId;
    private String subject;
    private String kind;
    private String authorRole;

    public SupportCaseUpdatedEvent() { super("SUPPORT_CASE_UPDATED", "crm-service"); }

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getAuthorRole() { return authorRole; }
    public void setAuthorRole(String authorRole) { this.authorRole = authorRole; }
}
