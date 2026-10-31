package com.waylo.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Maps a supplier organization to its provider sub merchant (iyzico Pazaryeri: subMerchantKey). */
@Entity
@Table(name = "supplier_payout_accounts")
public class SupplierPayoutAccount {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false, unique = true)
    private UUID organizationId;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "sub_merchant_key", nullable = false)
    private String subMerchantKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutAccountStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SupplierPayoutAccount() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getSubMerchantKey() { return subMerchantKey; }
    public void setSubMerchantKey(String subMerchantKey) { this.subMerchantKey = subMerchantKey; }
    public PayoutAccountStatus getStatus() { return status; }
    public void setStatus(PayoutAccountStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
