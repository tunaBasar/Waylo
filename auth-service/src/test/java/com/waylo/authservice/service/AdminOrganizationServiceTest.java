package com.waylo.authservice.service;

import com.waylo.authservice.domain.Organization;
import com.waylo.authservice.domain.OrganizationStatus;
import com.waylo.authservice.dto.OrganizationResponse;
import com.waylo.authservice.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOrganizationServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final String ORG_NAME = "Test Corp";

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private AdminOrganizationService adminOrganizationService;

    private Organization pendingOrg;
    private Organization activeOrg;
    private Organization archivedOrg;

    @BeforeEach
    void setUp() {
        pendingOrg = new Organization(ORG_NAME);
        pendingOrg.setStatus(OrganizationStatus.PENDING);

        activeOrg = new Organization(ORG_NAME + " Active");
        activeOrg.setStatus(OrganizationStatus.ACTIVE);

        archivedOrg = new Organization(ORG_NAME + " Archived");
        archivedOrg.setStatus(OrganizationStatus.ARCHIVED);
    }

    @Test
    void approve_pendingOrg_success() {
        // Arrange
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(pendingOrg));
        when(organizationRepository.save(any(Organization.class))).thenReturn(pendingOrg);

        // Act
        assertDoesNotThrow(() -> adminOrganizationService.approve(ORG_ID, ADMIN_ID));

        // Assert
        assertEquals(OrganizationStatus.ACTIVE, pendingOrg.getStatus());
        verify(organizationRepository).save(pendingOrg);
    }

    @Test
    void approve_activeOrg_throws409() {
        // Arrange
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(activeOrg));

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminOrganizationService.approve(ORG_ID, ADMIN_ID));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Organization is not pending", ex.getReason());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void approve_missingOrg_throws404() {
        // Arrange
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.empty());

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminOrganizationService.approve(ORG_ID, ADMIN_ID));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Organization not found", ex.getReason());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void suspend_activeOrg_success() {
        // Arrange
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(activeOrg));
        when(organizationRepository.save(any(Organization.class))).thenReturn(activeOrg);

        // Act
        assertDoesNotThrow(() -> adminOrganizationService.suspend(ORG_ID, ADMIN_ID, "Test reason"));

        // Assert
        assertEquals(OrganizationStatus.SUSPENDED, activeOrg.getStatus());
        verify(organizationRepository).save(activeOrg);
    }

    @Test
    void suspend_archivedOrg_throws409() {
        // Arrange
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(archivedOrg));

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminOrganizationService.suspend(ORG_ID, ADMIN_ID, "Test reason"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Organization is not active or pending", ex.getReason());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void list_defaultsToPending_andReportsTotal() {
        var pageable = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        when(organizationRepository.findByStatus(eq(OrganizationStatus.PENDING), pageable.capture()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(pendingOrg),
                        org.springframework.data.domain.PageRequest.of(0, 5), 11));
        var result = adminOrganizationService.list(null, 0, 500);
        assertEquals(11, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals(100, pageable.getValue().getPageSize());
    }

    @Test
    void list_statusAll_returnsEveryStatus() {
        when(organizationRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(pendingOrg, activeOrg, archivedOrg)));
        var result = adminOrganizationService.list("all", 0, 20);
        assertEquals(3, result.getContent().size());
        verify(organizationRepository, never()).findByStatus(any(OrganizationStatus.class), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void list_explicitStatus_filters() {
        when(organizationRepository.findByStatus(eq(OrganizationStatus.ACTIVE), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(activeOrg)));
        assertEquals(1, adminOrganizationService.list("active", 0, 20).getContent().size());
    }
}
