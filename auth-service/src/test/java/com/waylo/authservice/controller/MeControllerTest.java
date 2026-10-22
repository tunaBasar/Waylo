package com.waylo.authservice.controller;

import com.waylo.authservice.dto.ApiResponse;
import com.waylo.authservice.dto.AuthResponse;
import com.waylo.authservice.dto.ChangePasswordRequest;
import com.waylo.authservice.dto.UpdateProfileRequest;
import com.waylo.authservice.dto.UserProfileResponse;
import com.waylo.authservice.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MeControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private AccountService accountService;
    private MeController controller;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        controller = new MeController(accountService);
    }

    @Test
    void getProfile_success_returns200WithProfile() throws Exception {
        UserProfileResponse profile = new UserProfileResponse(
                USER_ID, "test@example.com", "John", "Doe", "USER", true,
                java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        when(accountService.getProfile(USER_ID)).thenReturn(profile);

        ResponseEntity<ApiResponse<UserProfileResponse>> response =
                controller.getProfile(USER_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        assertEquals("OK", response.getBody().message());
        assertEquals(USER_ID, response.getBody().data().id());
        assertEquals("test@example.com", response.getBody().data().email());
        assertEquals("John", response.getBody().data().firstName());
        assertEquals("Doe", response.getBody().data().lastName());
    }

    @Test
    void updateProfile_success_returns200() throws Exception {
        UserProfileResponse profile = new UserProfileResponse(
                USER_ID, "test@example.com", "Jane", "Smith", "USER", true,
                java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        when(accountService.updateProfile(eq(USER_ID), any())).thenReturn(profile);

        UpdateProfileRequest req = new UpdateProfileRequest("Jane", "Smith");
        ResponseEntity<ApiResponse<UserProfileResponse>> response =
                controller.updateProfile(USER_ID, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        assertEquals("Profil guncellendi", response.getBody().message());
        assertEquals("Jane", response.getBody().data().firstName());
        assertEquals("Smith", response.getBody().data().lastName());
    }

    @Test
    void changePassword_success_returns200() throws Exception {
        AuthResponse authResponse = new AuthResponse(
                "access-token", "refresh-token", USER_ID, "test@example.com",
                "John", "Doe", "USER", true);
        when(accountService.changePassword(eq(USER_ID), any())).thenReturn(authResponse);

        ChangePasswordRequest req = new ChangePasswordRequest("old123", "newPass123");
        ResponseEntity<ApiResponse<AuthResponse>> response =
                controller.changePassword(USER_ID, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        assertEquals("Sifre guncellendi", response.getBody().message());
        assertEquals("access-token", response.getBody().data().token());
        assertEquals("refresh-token", response.getBody().data().refreshToken());
    }

    @Test
    void getProfile_serviceNotFound_propagates404() {
        when(accountService.getProfile(USER_ID))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanici bulunamadi"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.getProfile(USER_ID));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
