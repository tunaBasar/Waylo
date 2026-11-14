package com.waylo.authservice.controller;

import com.waylo.authservice.service.AccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalAccessControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new InternalAccessController(mock(AccessService.class))).build();
    }

    @Test
    void invalidPermissionIs400() throws Exception {
        mvc.perform(get("/internal/access")
                        .param("userId", UUID.randomUUID().toString())
                        .param("organizationId", UUID.randomUUID().toString())
                        .param("permission", "NOT_A_PERMISSION"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidUuidIs400() throws Exception {
        mvc.perform(get("/internal/access")
                        .param("userId", "nope")
                        .param("organizationId", UUID.randomUUID().toString())
                        .param("permission", "HOTEL_MANAGE"))
                .andExpect(status().isBadRequest());
    }
}
