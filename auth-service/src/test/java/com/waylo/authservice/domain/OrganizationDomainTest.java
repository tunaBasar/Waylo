package com.waylo.authservice.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationDomainTest {

    @Test
    void organizationStartsPendingWithNormalizedName() {
        Organization organization = new Organization("  Waylo Hotels  ");
        assertEquals("Waylo Hotels", organization.getName());
        assertEquals(OrganizationStatus.PENDING, organization.getStatus());
        assertNull(organization.getId());
    }

    @Test
    void organizationRejectsMissingBlankAndOverlongNames() {
        assertThrows(IllegalArgumentException.class, () -> new Organization(null));
        assertThrows(IllegalArgumentException.class, () -> new Organization("  \t "));
        assertThrows(IllegalArgumentException.class, () -> new Organization("a".repeat(256)));
    }

    @Test
    void statusChangeBumpsPermissionVersionOnlyWhenChanged() {
        OrganizationMembership membership = membership();
        assertEquals(MembershipStatus.ACTIVE, membership.getStatus());
        assertEquals(0, membership.getPermissionVersion());
        membership.setStatus(MembershipStatus.SUSPENDED);
        assertEquals(1, membership.getPermissionVersion());
        membership.setStatus(MembershipStatus.SUSPENDED);
        assertEquals(1, membership.getPermissionVersion());
        membership.setStatus(MembershipStatus.REVOKED);
        assertEquals(2, membership.getPermissionVersion());
        membership.incrementPermissionVersion();
        assertEquals(3, membership.getPermissionVersion());
    }

    @Test
    void requiredReferencesAndStatusesCannotBeNull() {
        Organization organization = new Organization("Example");
        assertThrows(NullPointerException.class, () -> new OrganizationMembership(null, organization));
        assertThrows(NullPointerException.class, () -> new OrganizationMembership(new User(), null));
        assertThrows(NullPointerException.class, () -> organization.setStatus(null));
        assertThrows(NullPointerException.class, () -> membership().setStatus(null));
    }

    @Test
    void organizationOwnerScopeMatchesMembershipOrganization() {
        OrganizationMembership membership = membership();
        UUID organizationId = membership.getOrganization().getId();
        RoleAssignment assignment = new RoleAssignment(membership, Role.ORGANIZATION_OWNER,
                ScopeType.ORGANIZATION, organizationId);
        assertEquals(organizationId, assignment.getOrganizationId());
        assertEquals(organizationId, assignment.getScopeId());
    }

    @Test
    void wrongOrganizationScopeIsRejectedBeforePersistence() {
        assertThrows(IllegalArgumentException.class, () -> new RoleAssignment(membership(),
                Role.HOTEL_OWNER, ScopeType.ORGANIZATION, UUID.randomUUID()));
    }

    @Test
    void organizationOwnerCannotHaveHotelScope() {
        assertThrows(IllegalArgumentException.class, () -> new RoleAssignment(membership(),
                Role.ORGANIZATION_OWNER, ScopeType.HOTEL, UUID.randomUUID()));
    }

    @Test
    void hotelScopeIsOnlyARemoteReferenceAtThisLayer() {
        UUID hotelId = UUID.randomUUID();
        RoleAssignment assignment = new RoleAssignment(membership(), Role.HOTEL_STAFF, ScopeType.HOTEL, hotelId);
        assertEquals(hotelId, assignment.getScopeId());
        assertThrows(NullPointerException.class, () -> new RoleAssignment(membership(),
                Role.HOTEL_STAFF, ScopeType.HOTEL, null));
    }

    private OrganizationMembership membership() {
        Organization organization = new Organization("Example");
        // Unit fixture only. Real UUID generation is covered by the PostgreSQL integration suite.
        try {
            Field id = Organization.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(organization, UUID.randomUUID());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        return new OrganizationMembership(new User(), organization);
    }
}
