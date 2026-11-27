package com.waylo.flightservice.controller;

import com.waylo.flightservice.domain.FlightStatus;
import com.waylo.flightservice.dto.FlightResponse;
import com.waylo.flightservice.service.FlightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FlightControllerListTest {

    private final FlightService flightService = mock(FlightService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FlightController(flightService)).build();
        when(flightService.list(any(), any(), any(), anyBoolean(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.<FlightResponse>of(), PageRequest.of(0, 50), 510));
    }

    @Test
    void defaultsAreFirstPageOf50AndTotalHeader() throws Exception {
        mvc.perform(get("/api/flights"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "510"))
                .andExpect(jsonPath("$.data").isArray());
        verify(flightService).list(null, null, null, false, null, 0, 50);
    }

    @Test
    void passesAllFilters() throws Exception {
        mvc.perform(get("/api/flights")
                        .param("page", "3").param("size", "200").param("from", "2026-12-01")
                        .param("to", "2026-12-31").param("status", "CANCELLED").param("upcoming", "true")
                        .param("flightNumber", "WD1"))
                .andExpect(status().isOk());
        verify(flightService).list(LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-31"),
                FlightStatus.CANCELLED, true, "WD1", 3, 200);
    }
}
