package com.waylo.flightseatinventoryservice;

import com.waylo.flightseatinventoryservice.controller.FlightSeatController;
import com.waylo.flightseatinventoryservice.exception.*;
import com.waylo.flightseatinventoryservice.service.FlightSeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FlightSeatControllerTest {

    FlightSeatService service = Mockito.mock(FlightSeatService.class);
    MockMvc mvc;
    final String uid = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new FlightSeatController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator).build();
    }

    private int postSeat(String url, String json) throws Exception {
        return mvc.perform(post(url).header("X-User-Id", uid)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse().getStatus();
    }

    @Test
    void invalidPriceIs400() throws Exception {
        for (String price : new String[]{"0", "0.001", "-5", "123456789.00", "null"}) {
            org.junit.jupiter.api.Assertions.assertEquals(400, postSeat("/api/seat-inventory/seats",
                    "{\"flightNumber\":\"TK100\",\"seatNumber\":\"12A\",\"seatType\":\"ECONOMY\",\"price\":" + price + "}"),
                    price);
        }
    }

    @Test
    void invalidSeatNumberFromServiceIs400() throws Exception {
        when(service.createFlightSeat(any())).thenThrow(new IllegalArgumentException("bad seat"));
        org.junit.jupiter.api.Assertions.assertEquals(400, postSeat("/api/seat-inventory/seats",
                "{\"flightNumber\":\"TK100\",\"seatNumber\":\"99Z\",\"seatType\":\"ECONOMY\",\"price\":10}"));
    }

    @Test
    void invalidSeatTypeFromServiceIs400() throws Exception {
        when(service.createFlightSeat(any())).thenThrow(new IllegalArgumentException("bad type"));
        org.junit.jupiter.api.Assertions.assertEquals(400, postSeat("/api/seat-inventory/seats",
                "{\"flightNumber\":\"TK100\",\"seatNumber\":\"12A\",\"seatType\":\"X\",\"price\":10}"));
    }

    @Test
    void optimisticLockIs409() throws Exception {
        when(service.reserveSeat(any(), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Object.class, "x"));
        mvc.perform(post("/api/seat-inventory/seats/TK100/reserve").header("X-User-Id", uid)
                .contentType(MediaType.APPLICATION_JSON).content("{\"seatNumber\":\"12A\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void conflictForbiddenUnavailableMapping() throws Exception {
        when(service.reserveSeat(any(), any(), any()))
                .thenThrow(new ConflictException("SEAT_ALREADY_RESERVED", "x"));
        mvc.perform(post("/api/seat-inventory/seats/TK100/reserve").header("X-User-Id", uid)
                .contentType(MediaType.APPLICATION_JSON).content("{\"seatNumber\":\"12A\"}"))
                .andExpect(status().isConflict());

        Mockito.doThrow(new ForbiddenException("no")).when(service).cancelReservation(any(), any(), any(), Mockito.anyBoolean());
        mvc.perform(delete("/api/seat-inventory/seats/TK100/reserve").header("X-User-Id", uid)
                .contentType(MediaType.APPLICATION_JSON).content("{\"seatNumber\":\"12A\"}"))
                .andExpect(status().isForbidden());

        Mockito.doThrow(new ServiceUnavailableException("down", null)).when(service).reserveSeat(any(), any(), any());
        mvc.perform(post("/api/seat-inventory/seats/TK100/reserve").header("X-User-Id", uid)
                .contentType(MediaType.APPLICATION_JSON).content("{\"seatNumber\":\"12A\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void deleteSeatIs204() throws Exception {
        mvc.perform(delete("/api/seat-inventory/seats/TK100/12A")).andExpect(status().isNoContent());
    }
}
