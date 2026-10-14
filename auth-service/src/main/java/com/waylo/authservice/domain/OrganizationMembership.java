package com.waylo.authservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Membership identity is immutable. Revoke a membership rather than moving it. */
@Entity
@Table(name = "organization_memberships",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_membership_user_organization", columnNames = {"user_id", "organization_id"}),
                @UniqueConstraint(name = "uq_membership_id_organization", columnNames = {"id", "organization_id"})
        },
        indexes = @Index(name = "idx_memberships_organization_status", columnList = "organization_id,status"))
public class OrganizationMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @PositiveOrZero
    @Column(name = "permission_version", nullable = false)
    private long permissionVersion;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganizationMembership() {}

    public OrganizationMembership(User user, Organization organization) {
        this.user = Objects.requireNonNull(user, "User is required");
        this.organization = Objects.requireNonNull(organization, "Organization is required");
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public Organization getOrganization() { return organization; }
    public MembershipStatus getStatus() { return status; }
    public long getPermissionVersion() { return permissionVersion; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(MembershipStatus status) {
        Objects.requireNonNull(status, "Membership status is required");
        if (this.status != status) {
            incrementPermissionVersion();
            this.status = status;
        }
    }

    /** The future grant/revoke service must call this in the same transaction as a role change. */
    public void incrementPermissionVersion() {
        permissionVersion = Math.incrementExact(permissionVersion);
    }
}
