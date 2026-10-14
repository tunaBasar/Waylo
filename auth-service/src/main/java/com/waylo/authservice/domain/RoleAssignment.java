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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable scoped grant. A grant service must authorize the actor and validate
 * a HOTEL scope with hotel-service before creating it. This entity is not an authorization service.
 */
@Entity
@Table(name = "role_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uq_membership_role_scope",
                columnNames = {"membership_id", "role", "scope_type", "scope_id"}),
        indexes = @Index(name = "idx_assignments_organization_scope",
                columnList = "organization_id,scope_type,scope_id"))
public class RoleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false, updatable = false)
    private OrganizationMembership membership;

    // Deliberately stored for a composite DB FK and a local organization-scope CHECK.
    @NotNull
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private Role role;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, updatable = false, length = 20)
    private ScopeType scopeType;

    @NotNull
    @Column(name = "scope_id", nullable = false, updatable = false)
    private UUID scopeId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RoleAssignment() {}

    public RoleAssignment(OrganizationMembership membership, Role role, ScopeType scopeType, UUID scopeId) {
        this.membership = Objects.requireNonNull(membership, "Membership is required");
        this.organizationId = Objects.requireNonNull(membership.getOrganization().getId(),
                "Persist the organization before creating a role assignment");
        this.role = Objects.requireNonNull(role, "Role is required");
        this.scopeType = Objects.requireNonNull(scopeType, "Scope type is required");
        this.scopeId = Objects.requireNonNull(scopeId, "Scope ID is required");
        validateScope();
    }

    @PrePersist
    @PreUpdate
    private void validateScope() {
        if (membership == null || organizationId == null || role == null || scopeType == null || scopeId == null) {
            throw new IllegalArgumentException("Membership, organization, role and scope are required");
        }
        if (!organizationId.equals(membership.getOrganization().getId())) {
            throw new IllegalArgumentException("Role assignment must belong to the membership's organization");
        }
        if (scopeType == ScopeType.ORGANIZATION && !scopeId.equals(organizationId)) {
            throw new IllegalArgumentException("Organization scope must match the membership's organization");
        }
        if (role == Role.ORGANIZATION_OWNER && scopeType != ScopeType.ORGANIZATION) {
            throw new IllegalArgumentException("ORGANIZATION_OWNER requires organization scope");
        }
    }

    public UUID getId() { return id; }
    public OrganizationMembership getMembership() { return membership; }
    public UUID getOrganizationId() { return organizationId; }
    public Role getRole() { return role; }
    public ScopeType getScopeType() { return scopeType; }
    public UUID getScopeId() { return scopeId; }
    public Instant getCreatedAt() { return createdAt; }
}
