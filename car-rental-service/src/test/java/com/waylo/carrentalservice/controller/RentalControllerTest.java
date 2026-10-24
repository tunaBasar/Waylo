package com.waylo.carrentalservice.controller;

import com.waylo.carrentalservice.dto.RentalResponse;
import com.waylo.carrentalservice.domain.RentalStatus;
import com.waylo.carrentalservice.exception.BusinessRuleException;
import com.waylo.carrentalservice.exception.ConflictException;
import com.waylo.carrentalservice.exception.ForbiddenException;
import com.waylo.carrentalservice.exception.GlobalExceptionHandler;
import com.waylo.carrentalservice.exception.ResourceNotFoundException;
import com.waylo.carrentalservice.service.RentalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RentalControllerTest {

    RentalService service = mock(RentalService.class);
    MockMvc mvc;
    UUID user = UUID.randomUUID();
    UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RentalController(service), new VehicleAvailabilityController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private RentalResponse sample() {
        return new RentalResponse(id, UUID.randomUUID(), user, LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3), BigDecimal.TEN, BigDecimal.valueOf(20), RentalStatus.RESERVED, null);
    }

    private String body() {
        return "{\"vehicleId\":\"" + UUID.randomUUID() + "\",\"pickupDate\":\"2030-01-01\",\"returnDate\":\"2030-01-03\"}";
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create(any(), any())).thenReturn(sample());
        mvc.perform(post("/api/rentals").header("X-User-Id", user.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated());
    }

    @Test
    void createWithoutHeaderIs401() throws Exception {
        mvc.perform(post("/api/rentals").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createInvalidBodyIs400() throws Exception {
        mvc.perform(post("/api/rentals").header("X-User-Id", user.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOverlapIs409() throws Exception {
        when(service.create(any(), any())).thenThrow(new ConflictException("VEHICLE_NOT_AVAILABLE", "x"));
        mvc.perform(post("/api/rentals").header("X-User-Id", user.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VEHICLE_NOT_AVAILABLE"));
    }

    @Test
    void createNotRentableIs422() throws Exception {
        when(service.create(any(), any())).thenThrow(new BusinessRuleException("VEHICLE_NOT_RENTABLE", "x"));
        mvc.perform(post("/api/rentals").header("X-User-Id", user.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createBadDatesIs400() throws Exception {
        when(service.create(any(), any())).thenThrow(new IllegalArgumentException("bad"));
        mvc.perform(post("/api/rentals").header("X-User-Id", user.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meReturns200() throws Exception {
        when(service.listMine(user)).thenReturn(List.of(sample()));
        mvc.perform(get("/api/rentals/me").header("X-User-Id", user.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(id.toString()));
    }

    @Test
    void getStrangerIs404() throws Exception {
        when(service.get(any(), anyBoolean(), any())).thenThrow(new ResourceNotFoundException("Rental", "id", id));
        mvc.perform(get("/api/rentals/" + id).header("X-User-Id", user.toString()).header("X-User-Role", "USER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelOk422And409() throws Exception {
        when(service.cancel(user, false, id)).thenReturn(sample());
        mvc.perform(post("/api/rentals/" + id + "/cancel").header("X-User-Id", user.toString()))
                .andExpect(status().isOk());

        when(service.cancel(user, true, id)).thenThrow(new BusinessRuleException("RENTAL_ALREADY_CANCELLED", "x"));
        mvc.perform(post("/api/rentals/" + id + "/cancel").header("X-User-Id", user.toString()).header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnprocessableEntity());

        UUID other = UUID.randomUUID();
        when(service.cancel(other, false, id)).thenThrow(new OptimisticLockingFailureException("v"));
        mvc.perform(post("/api/rentals/" + id + "/cancel").header("X-User-Id", other.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void adminListForbiddenForUserOkForAdmin() throws Exception {
        when(service.listByVehicle(false, null)).thenThrow(new ForbiddenException("no"));
        when(service.listByVehicle(true, null)).thenReturn(List.of());
        mvc.perform(get("/api/rentals").header("X-User-Id", user.toString()).header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/rentals").header("X-User-Id", user.toString()).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void availableOkAndBadRange() throws Exception {
        when(service.findAvailable(any(), any(), any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/vehicles/available?from=2030-01-01&to=2030-01-03")).andExpect(status().isOk());

        when(service.findAvailable(any(), any(), any(), any())).thenThrow(new IllegalArgumentException("bad"));
        mvc.perform(get("/api/vehicles/available?from=2030-01-05&to=2030-01-03")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/vehicles/available?from=garbage&to=2030-01-03")).andExpect(status().isBadRequest());
    }
}
