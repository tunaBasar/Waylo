package com.waylo.authservice.controller;

import com.waylo.authservice.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeControllerDeleteTest {
    private final UUID id = UUID.randomUUID();
    private AccountService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AccountService.class);
        mvc = MockMvcBuilders.standaloneSetup(new MeController(service))
                .setControllerAdvice(new AuthErrorAdvice()).build();
    }

    private ResultActions del(String body) throws Exception {
        var b = delete("/api/me").header("X-User-Id", id.toString());
        if (body != null) b = b.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(b);
    }

    @Test
    void delete_success_204() throws Exception {
        del("{\"password\":\"pw\"}").andExpect(status().isNoContent());
        verify(service).deleteAccount(id, "pw");
    }

    @Test
    void delete_wrongPassword_401() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "x")).when(service).deleteAccount(any(), any());
        del("{\"password\":\"bad\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void delete_soleOwner_422() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "SOLE_OWNER")).when(service).deleteAccount(any(), any());
        del("{\"password\":\"pw\"}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void delete_missingBody_400() throws Exception {
        del(null).andExpect(status().is4xxClientError());
        verifyNoInteractions(service);
    }

    @Test
    void delete_blankPassword_400() throws Exception {
        del("{\"password\":\"\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
