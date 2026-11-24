package com.waylo.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** One bank account per organization, replaced in place (version + 1, back to PENDING_VERIFICATION). Holds ciphertext only. */
@Entity
@Table(name = "partner_bank_accounts")
public class PartnerBankAccount {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "account_holder_name", nullable = false, length = 140)
    private String accountHolderName;

    @Column(name = "iban_ciphertext", nullable = false, columnDefinition = "text")
    private String ibanCiphertext;

    @Column(name = "iban_last4", nullable = false, length = 4, columnDefinition = "bpchar(4)")
    private String ibanLast4;

    @Column(name = "iban_hash", nullable = false, length = 64, columnDefinition = "bpchar(64)")
    private String ibanHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private BankAccountStatus status;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PartnerBankAccount() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getAccountHolderName() { return accountHolderName; }
    public void setAccountHolderName(String accountHolderName) { this.accountHolderName = accountHolderName; }
    public String getIbanCiphertext() { return ibanCiphertext; }
    public void setIbanCiphertext(String ibanCiphertext) { this.ibanCiphertext = ibanCiphertext; }
    public String getIbanLast4() { return ibanLast4; }
    public void setIbanLast4(String ibanLast4) { this.ibanLast4 = ibanLast4; }
    public String getIbanHash() { return ibanHash; }
    public void setIbanHash(String ibanHash) { this.ibanHash = ibanHash; }
    public BankAccountStatus getStatus() { return status; }
    public void setStatus(BankAccountStatus status) { this.status = status; }
    public UUID getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(UUID verifiedBy) { this.verifiedBy = verifiedBy; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public Long getVersion() { return version; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
