package com.waylo.authservice.controller;

import com.waylo.authservice.domain.OrganizationStatus;
import com.waylo.authservice.dto.ApiResponse;
import com.waylo.authservice.dto.OrganizationResponse;
import com.waylo.authservice.dto.SuspendOrganizationRequest;
import com.waylo.authservice.service.AdminOrganizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AdminOrganizationControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    private AdminOrganizationService adminService;
    private AdminOrganizationController controller;

    @BeforeEach
    void setUp() {
        adminService = mock(AdminOrganizationService.class);
        controller = new AdminOrganizationController(adminService);
    }

    @Test
    void listPending_userRole_returns403() {
        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.list("PENDING", 0, 100, ADMIN_ID, "USER"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Platform admin required", ex.getReason());
        verifyNoInteractions(adminService);
    }

    @Test
    void listPending_adminRole_delegatesToService() {
        // Arrange
        when(adminService.list("PENDING", 0, 100)).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        // Act
        ResponseEntity<com.waylo.authservice.dto.PagedApiResponse<OrganizationResponse>> response =
                controller.list("PENDING", 0, 100, ADMIN_ID, "ADMIN");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        assertEquals(0L, response.getBody().total());
        verify(adminService).list("PENDING", 0, 100);
    }

    @Test
    void approve_userRole_returns403() {
        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.approve(ORG_ID, ADMIN_ID, "USER"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Platform admin required", ex.getReason());
        verifyNoInteractions(adminService);
    }

    @Test
    void approve_adminRole_delegatesToService() {
        // Act
        ResponseEntity<ApiResponse<Void>> response = controller.approve(ORG_ID, ADMIN_ID, "ADMIN");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        verify(adminService).approve(ORG_ID, ADMIN_ID);
    }

    @Test
    void suspend_userRole_returns403() {
        SuspendOrganizationRequest req = new SuspendOrganizationRequest("Test reason");

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.suspend(ORG_ID, req, ADMIN_ID, "USER"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Platform admin required", ex.getReason());
        verifyNoInteractions(adminService);
    }

    @Test
    void suspend_adminRole_delegatesToService() {
        SuspendOrganizationRequest req = new SuspendOrganizationRequest("Test reason");

        // Act
        ResponseEntity<ApiResponse<Void>> response = controller.suspend(ORG_ID, req, ADMIN_ID, "ADMIN");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        verify(adminService).suspend(ORG_ID, ADMIN_ID, "Test reason");
    }
}
