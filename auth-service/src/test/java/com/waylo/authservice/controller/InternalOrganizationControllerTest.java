package com.waylo.authservice.controller;

import com.waylo.authservice.domain.Organization;
import com.waylo.authservice.domain.OrganizationStatus;
import com.waylo.authservice.repository.OrganizationRepository;
import com.waylo.authservice.repository.RoleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalOrganizationControllerTest {

    private OrganizationRepository organizationRepository;
    private RoleAssignmentRepository roleAssignmentRepository;
    private InternalOrganizationController controller;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        controller = new InternalOrganizationController(organizationRepository, roleAssignmentRepository);
    }

    @Test
    void activeIds_returnsOnlyActiveOrganizationIds() throws Exception {
        Organization active1 = buildOrganization(UUID.randomUUID());
        Organization active2 = buildOrganization(UUID.randomUUID());

        when(organizationRepository.findByStatus(OrganizationStatus.ACTIVE))
                .thenReturn(List.of(active1, active2));

        List<UUID> result = controller.activeIds();

        assertEquals(List.of(active1.getId(), active2.getId()), result);
        verify(organizationRepository).findByStatus(eq(OrganizationStatus.ACTIVE));
    }

    @Test
    void activeIds_noActiveOrganizations_returnsEmptyList() {
        when(organizationRepository.findByStatus(OrganizationStatus.ACTIVE)).thenReturn(List.of());

        List<UUID> result = controller.activeIds();

        assertEquals(List.of(), result);
    }

    @Test
    void getOrganization_returnsDetailWithTypes() throws Exception {
        // Use the 3-arg constructor to set businessType and legalType
        Constructor<Organization> ctor = Organization.class.getDeclaredConstructor(
                String.class, com.waylo.authservice.domain.BusinessType.class,
                com.waylo.authservice.domain.LegalType.class);
        ctor.setAccessible(true);
        Organization org = ctor.newInstance("Test Org",
                com.waylo.authservice.domain.BusinessType.CAR_RENTAL,
                com.waylo.authservice.domain.LegalType.INDIVIDUAL);
        Field idField = Organization.class.getDeclaredField("id");
        idField.setAccessible(true);
        UUID orgId = UUID.randomUUID();
        idField.set(org, orgId);

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        InternalOrganizationController.InternalOrganizationDetail detail =
                controller.getOrganization(orgId);

        assertEquals(orgId, detail.id());
        assertEquals("Test Org", detail.name());
        assertEquals(com.waylo.authservice.domain.OrganizationStatus.PENDING, detail.status());
        assertEquals(com.waylo.authservice.domain.BusinessType.CAR_RENTAL, detail.businessType());
        assertEquals(com.waylo.authservice.domain.LegalType.INDIVIDUAL, detail.legalType());
        verify(organizationRepository).findById(orgId);
    }

    @Test
    void getOrganization_returns404ForMissing() {
        UUID missing = UUID.randomUUID();
        when(organizationRepository.findById(missing)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> controller.getOrganization(missing));
    }

    @Test
    void contacts_returnsOwnerEmailsFromRepository() {
        UUID orgId = UUID.randomUUID();
        when(roleAssignmentRepository.findOwnerEmails(orgId)).thenReturn(List.of("a@x.com", "b@x.com"));
        assertEquals(List.of("a@x.com", "b@x.com"), controller.contacts(orgId).emails());
    }

    @Test
    void ownerEmailsQuery_filtersActiveMembersAndOwnerRolesOnly() throws Exception {
        String q = RoleAssignmentRepository.class.getMethod("findOwnerEmails", UUID.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
        org.junit.jupiter.api.Assertions.assertTrue(q.contains("MembershipStatus.ACTIVE"));
        org.junit.jupiter.api.Assertions.assertTrue(q.contains("Role.ORGANIZATION_OWNER"));
        org.junit.jupiter.api.Assertions.assertTrue(q.contains("Role.HOTEL_OWNER"));
        org.junit.jupiter.api.Assertions.assertFalse(q.contains("HOTEL_MANAGER") || q.contains("HOTEL_STAFF"));
        org.junit.jupiter.api.Assertions.assertTrue(q.contains("r.organizationId = :organizationId"));
    }

    @Test
    void contactsRequiresServiceAuth_notificationServiceIsAllowlistedOnInternalOrganizations() throws Exception {
        String yml = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/application.yml"));
        int i = yml.indexOf("path: /internal/organizations");
        org.junit.jupiter.api.Assertions.assertTrue(i > 0);
        String line = yml.substring(i, yml.indexOf('\n', yml.indexOf("callers:", i)));
        org.junit.jupiter.api.Assertions.assertTrue(line.contains("notification-service"));
        org.junit.jupiter.api.Assertions.assertTrue(line.contains("payment-service"));
    }

    /** Organization's constructor and ID are package-private/generated; set the id via reflection for this test double. */
    private Organization buildOrganization(UUID id) throws Exception {
        Constructor<Organization> ctor = Organization.class.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        Organization org = ctor.newInstance("Org " + id);
        Field idField = Organization.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(org, id);
        return org;
    }
}
