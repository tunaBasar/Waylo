package com.waylo.authservice.controller;

import com.waylo.authservice.dto.ApiResponse;
import com.waylo.authservice.dto.AuthResponse;
import com.waylo.authservice.service.AuthService;
import com.waylo.authservice.exception.DuplicateRegistrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Nested
    @DisplayName("POST /api/auth/register")
    class RegisterTests {

        @Test
        @DisplayName("Duplicate email -> HTTP 409 with the backend message")
        void duplicateEmail_returns409() throws Exception {
            when(authService.register(any())).thenThrow(
                    new DuplicateRegistrationException("Bu e-posta adresi zaten kullaniliyor"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"a@b.com\",\"password\":\"pass123\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Bu e-posta adresi zaten kullaniliyor"));
        }

        @Test
        @DisplayName("Other registration failure -> HTTP 400")
        void otherFailure_returns400() throws Exception {
            when(authService.register(any())).thenReturn(ApiResponse.error("Kayit basarisiz"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"a@b.com\",\"password\":\"pass123\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Kayit basarisiz"));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class LoginTests {

        @Test
        @DisplayName("Locked user -> HTTP 429")
        void lockedUser_returns429() throws Exception {
            when(authService.login(any())).thenReturn(
                    ApiResponse.error("Cok fazla hatali giris. Lutfen daha sonra tekrar deneyin.")
            );

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"test@test.com\",\"password\":\"wrong\"}"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Cok fazla hatali giris. Lutfen daha sonra tekrar deneyin."));
        }

        @Test
        @DisplayName("Wrong password -> HTTP 401")
        void wrongPassword_returns401() throws Exception {
            when(authService.login(any())).thenReturn(
                    ApiResponse.error("E-posta veya sifre hatali")
            );

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"test@test.com\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("E-posta veya sifre hatali"));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class RefreshTests {

        @Test
        @DisplayName("Empty body -> HTTP 401 without calling service")
        void emptyBody_returns401() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Gecersiz refresh token"));

            verify(authService, never()).refreshToken(any());
        }

        @Test
        @DisplayName("Missing refreshToken key -> HTTP 401")
        void missingRefreshToken_returns401() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"other\":\"value\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Gecersiz refresh token"));

            verify(authService, never()).refreshToken(any());
        }
    }
}
