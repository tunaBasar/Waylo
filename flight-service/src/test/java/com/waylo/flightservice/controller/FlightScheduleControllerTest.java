package com.waylo.flightservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waylo.flightservice.filter.AdminWriteFilter;
import com.waylo.flightservice.service.FlightScheduleService;
import com.waylo.flightservice.service.FlightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FlightScheduleControllerTest {

    private final FlightScheduleService scheduleService = mock(FlightScheduleService.class);
    private final FlightService flightService = mock(FlightService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new FlightController(flightService), new FlightScheduleController(scheduleService))
                .addFilters(new AdminWriteFilter(new ObjectMapper()))
                .build();
    }

    @Test
    void getSchedulesIsPublicAndNotParsedAsFlightId() throws Exception {
        when(scheduleService.list()).thenReturn(List.of());

        mvc.perform(get("/api/flights/schedules")).andExpect(status().isOk());

        verify(scheduleService).list();
        verify(flightService, never()).getById(any());
    }

    @Test
    void getFlightByUuidStillWorks() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/flights/" + id)).andExpect(status().isOk());
        verify(flightService).getById(id);
    }

    @Test
    void anonymousPostIsUnauthorized() throws Exception {
        mvc.perform(post("/api/flights/schedules").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verify(scheduleService, never()).create(any());
    }

    @Test
    void nonAdminWritesAreForbidden() throws Exception {
        for (String path : List.of("/api/flights/schedules", "/api/flights/schedules/apply",
                "/api/flights/schedules/" + UUID.randomUUID() + "/activate",
                "/api/flights/schedules/" + UUID.randomUUID() + "/deactivate")) {
            mvc.perform(post(path).header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "USER").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        verify(scheduleService, never()).create(any());
        verify(scheduleService, never()).applyAll();
    }

    @Test
    void adminApplyReachesApplyHandlerNotUuidHandler() throws Exception {
        mvc.perform(post("/api/flights/schedules/apply").header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
        verify(scheduleService).applyAll();
    }
}
