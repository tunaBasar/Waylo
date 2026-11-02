package com.waylo.packagebookingservice.controller;

import com.waylo.packagebookingservice.dto.FlightAffectedItem;
import com.waylo.packagebookingservice.service.OrderService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InternalOrderControllerTest {

    @Test
    void flightItemsDelegatesAndWraps() {
        var service = mock(OrderService.class);
        var controller = new InternalOrderController(service);
        var item = new FlightAffectedItem(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(service.findAffectedFlightItems("WY123", LocalDate.of(2026, 11, 5))).thenReturn(List.of(item));

        var res = controller.flightItems("WY123", LocalDate.of(2026, 11, 5));

        assertThat(res.getBody().data()).containsExactly(item);
    }
}
