package com.waylo.hotelservice.service;

import com.waylo.hotelservice.dto.AvailabilityResponse;
import com.waylo.hotelservice.dto.AvailableOfferResponse;
import com.waylo.hotelservice.dto.HotelResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class HotelSearchServiceTest {
    private final HotelService hotels = mock(HotelService.class);
    private final AvailabilityService availability = mock(AvailabilityService.class);
    private final HotelSearchService service = new HotelSearchService(hotels, availability,
            Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC));
    private final LocalDate checkIn = LocalDate.of(2026, 10, 25);
    private final LocalDate checkOut = checkIn.plusDays(2);

    private HotelResponse hotel(UUID id) {
        return new HotelResponse(id, UUID.randomUUID(), "Search fixture", "Izmir", "TR", "Test street",
                4, "Test", BigDecimal.ZERO, BigDecimal.ZERO, true, 0, null, null);
    }

    @Test
    void onlyWholeStaySellableHotelsSurviveFilteringAndGuestCountsAreForwarded() {
        var available = hotel(UUID.randomUUID());
        var unavailable = hotel(UUID.randomUUID());
        when(hotels.search("Izmir", "TR", 4)).thenReturn(List.of(unavailable, available));
        when(availability.getAvailability(unavailable.id(), checkIn, checkOut, 2, 2, List.of(7)))
                .thenReturn(new AvailabilityResponse(unavailable.id(), checkIn, checkOut, 2, 2, 2, List.of(7), 3, List.of()));
        when(availability.getAvailability(available.id(), checkIn, checkOut, 2, 2, List.of(7)))
                .thenReturn(new AvailabilityResponse(available.id(), checkIn, checkOut, 2, 2, 2, List.of(7), 3,
                        List.of(mock(AvailableOfferResponse.class))));

        assertThat(service.search("Izmir", "TR", 4, checkIn, checkOut, 2, 2, List.of(7)))
                .extracting(HotelResponse::id).containsExactly(available.id());
        verify(availability).getAvailability(unavailable.id(), checkIn, checkOut, 2, 2, List.of(7));
        verify(availability).getAvailability(available.id(), checkIn, checkOut, 2, 2, List.of(7));
    }

    @Test
    void invalidStaysRejectedBeforeEmptyCatalogCanHideValidationErrors() {
        for (LocalDate end : List.of(checkIn, checkIn.minusDays(1), checkIn.plusDays(31))) {
            assertThatThrownBy(() -> service.search(null, null, null, checkIn, end, 1, 1, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.search(null, null, null, checkIn.minusDays(4), checkOut, 1, 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search(null, null, null, checkIn, null, 1, 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(hotels, availability);
    }

    @Test
    void invalidGuestCountsAndNullChildAgesNeverReachAvailability() {
        assertThatThrownBy(() -> service.search(null, null, null, checkIn, checkOut, 0, 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search(null, null, null, checkIn, checkOut, 1, 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search(null, null, null, checkIn, checkOut, 1, 1, List.of(18)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search(null, null, null, checkIn, checkOut, 1, 1, Arrays.asList((Integer) null)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(hotels, availability);
    }

    @Test
    void absentChildrenDefaultsToEmptyList() {
        var candidate = hotel(UUID.randomUUID());
        when(hotels.search(null, null, null)).thenReturn(List.of(candidate));
        when(availability.getAvailability(candidate.id(), checkIn, checkOut, 1, 1, List.of()))
                .thenReturn(new AvailabilityResponse(candidate.id(), checkIn, checkOut, 2, 1, 1, List.of(), 1, List.of()));
        assertThat(service.search(null, null, null, checkIn, checkOut, 1, 1, null)).isEmpty();
        verify(availability).getAvailability(candidate.id(), checkIn, checkOut, 1, 1, List.of());
    }
}
