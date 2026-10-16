package com.waylo.authservice.controller;

import com.waylo.authservice.domain.Organization;
import com.waylo.authservice.domain.OrganizationStatus;
import com.waylo.authservice.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalOrganizationControllerTest {

    private OrganizationRepository organizationRepository;
    private InternalOrganizationController controller;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        controller = new InternalOrganizationController(organizationRepository);
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
