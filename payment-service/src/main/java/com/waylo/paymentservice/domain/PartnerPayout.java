package com.waylo.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A partner payout request (PAYOUT-CONTRACT.md). Mutable status, history lives in payout_events and payout_ledger. */
@Entity
@Table(name = "partner_payouts")
public class PartnerPayout {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "bpchar(3)", updatable = false)
    private String currency;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PayoutStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 150, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "bpchar(64)", updatable = false)
    private String requestHash;

    @Column(name = "bank_account_id", nullable = false, updatable = false)
    private UUID bankAccountId;

    @Column(name = "bank_account_version", nullable = false, updatable = false)
    private Long bankAccountVersion;

    @Column(name = "iban_ciphertext", nullable = false, columnDefinition = "text", updatable = false)
    private String ibanCiphertext;

    @Column(name = "iban_last4", nullable = false, length = 4, columnDefinition = "bpchar(4)", updatable = false)
    private String ibanLast4;

    @Column(name = "account_holder_name", nullable = false, length = 140, updatable = false)
    private String accountHolderName;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_by_email", length = 255, updatable = false)
    private String requestedByEmail;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "processing_at")
    private Instant processingAt;

    @Column(name = "paid_by")
    private UUID paidBy;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by_type", length = 8)
    private PayoutActorType cancelledByType;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PartnerPayout() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public PayoutStatus getStatus() { return status; }
    public void setStatus(PayoutStatus status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public UUID getBankAccountId() { return bankAccountId; }
    public void setBankAccountId(UUID bankAccountId) { this.bankAccountId = bankAccountId; }
    public Long getBankAccountVersion() { return bankAccountVersion; }
    public void setBankAccountVersion(Long bankAccountVersion) { this.bankAccountVersion = bankAccountVersion; }
    public String getIbanCiphertext() { return ibanCiphertext; }
    public void setIbanCiphertext(String ibanCiphertext) { this.ibanCiphertext = ibanCiphertext; }
    public String getIbanLast4() { return ibanLast4; }
    public void setIbanLast4(String ibanLast4) { this.ibanLast4 = ibanLast4; }
    public String getAccountHolderName() { return accountHolderName; }
    public void setAccountHolderName(String accountHolderName) { this.accountHolderName = accountHolderName; }
    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }
    public String getRequestedByEmail() { return requestedByEmail; }
    public void setRequestedByEmail(String requestedByEmail) { this.requestedByEmail = requestedByEmail; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public Instant getProcessingAt() { return processingAt; }
    public void setProcessingAt(Instant processingAt) { this.processingAt = processingAt; }
    public UUID getPaidBy() { return paidBy; }
    public void setPaidBy(UUID paidBy) { this.paidBy = paidBy; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public UUID getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(UUID cancelledBy) { this.cancelledBy = cancelledBy; }
    public PayoutActorType getCancelledByType() { return cancelledByType; }
    public void setCancelledByType(PayoutActorType cancelledByType) { this.cancelledByType = cancelledByType; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public Long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
