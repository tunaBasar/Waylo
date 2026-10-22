package com.waylo.hotelservice.service;

import com.waylo.hotelservice.dto.HotelCurrencyResponse;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.DailyRateRepository;
import com.waylo.hotelservice.repository.HotelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateServiceCurrencyTest {

    private static final UUID HOTEL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private DailyRateRepository dailyRateRepository;
    @Mock
    private HotelRepository hotelRepository;

    private RateServiceImpl service;

    @BeforeEach
    void setUp() {
        // ratePlanRepository and roomTypeRepository not needed for getHotelCurrency
        service = new RateServiceImpl(
                dailyRateRepository,
                hotelRepository,
                null,
                null,
                null);
    }

    @Test
    void getHotelCurrency_existingCurrency_returnsResponse() {
        when(hotelRepository.existsById(HOTEL_ID)).thenReturn(true);
        when(dailyRateRepository.findCurrenciesByHotelId(HOTEL_ID))
                .thenReturn(List.of("USD"));

        HotelCurrencyResponse result = service.getHotelCurrency(HOTEL_ID);

        assertThat(result).isNotNull();
        assertThat(result.currency()).isEqualTo("USD");
    }

    @Test
    void getHotelCurrency_emptyRateTable_returnsNull() {
        when(hotelRepository.existsById(HOTEL_ID)).thenReturn(true);
        when(dailyRateRepository.findCurrenciesByHotelId(HOTEL_ID))
                .thenReturn(List.of());

        HotelCurrencyResponse result = service.getHotelCurrency(HOTEL_ID);

        assertThat(result).isNull();
        verify(dailyRateRepository).findCurrenciesByHotelId(HOTEL_ID);
    }

    @Test
    void getHotelCurrency_unknownHotel_throwsResourceNotFound() {
        when(hotelRepository.existsById(HOTEL_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getHotelCurrency(HOTEL_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Hotel");
    }
}
