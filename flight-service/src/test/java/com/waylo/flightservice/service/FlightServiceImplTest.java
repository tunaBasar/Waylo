package com.waylo.flightservice.service;

import com.waylo.flightservice.domain.Airport;
import com.waylo.flightservice.domain.Flight;
import com.waylo.flightservice.domain.FlightStatus;
import com.waylo.flightservice.dto.FlightMapper;
import com.waylo.flightservice.dto.FlightRequest;
import com.waylo.flightservice.dto.FlightResponse;
import com.waylo.flightservice.event.FlightCancelledEvent;
import com.waylo.flightservice.event.FlightCreatedEvent;
import com.waylo.flightservice.event.FlightEventPublisher;
import com.waylo.flightservice.exception.BusinessRuleException;
import com.waylo.flightservice.exception.DuplicateResourceException;
import com.waylo.flightservice.exception.ResourceNotFoundException;
import com.waylo.flightservice.repository.AirportRepository;
import com.waylo.flightservice.repository.FlightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightServiceImplTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

    @Mock
    private FlightRepository flightRepository;
    @Mock
    private AirportRepository airportRepository;
    @Mock
    private FlightMapper flightMapper;
    @Mock
    private FlightEventPublisher eventPublisher;

    private FlightServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FlightServiceImpl(flightRepository, airportRepository, flightMapper, eventPublisher, FIXED_CLOCK);
    }

    private Airport buildAirport(String iataCode, String timezone) {
        Airport a = new Airport();
        a.setId(UUID.randomUUID());
        a.setIataCode(iataCode);
        a.setName(iataCode + " Airport");
        a.setTimezone(timezone);
        return a;
    }

    private Airport buildAirportIST() {
        return buildAirport("IST", "Europe/Istanbul");
    }

    private Airport buildAirportJFK() {
        return buildAirport("JFK", "America/New_York");
    }

    private FlightRequest buildRequest(String flightNumber, String from, String to) {
        return new FlightRequest(flightNumber, from, to, "Waylo Air",
                Instant.parse("2026-12-01T07:00:00Z"), Instant.parse("2026-12-01T09:00:00Z"));
    }

    private Flight buildFlight(String flightNumber, FlightStatus status, Instant dep, Instant arr) {
        Flight f = new Flight();
        f.setId(UUID.randomUUID());
        f.setFlightNumber(flightNumber);
        f.setDepartureAirport(buildAirportIST());
        f.setArrivalAirport(buildAirportJFK());
        f.setDepartureTime(dep);
        f.setArrivalTime(arr);
        f.setAirline("Waylo Air");
        f.setStatus(status);
        f.setVersion(0L);
        return f;
    }

    // ===================== create =====================

    @Test
    void create_duplicateFlightNumber_throwsDuplicateResourceException() {
        FlightRequest request = buildRequest("WA100", "IST", "JFK");
        when(flightRepository.existsByFlightNumber("WA100")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateResourceException.class);
        verify(airportRepository, never()).findByIataCode(any());
    }

    @Test
    void create_valid_setsAirportsScheduledAndPublishesEvent() {
        FlightRequest request = buildRequest("WA100", "IST", "JFK");
        Airport departure = buildAirportIST();
        Airport arrival = buildAirportJFK();
        Flight mapped = new Flight();
        mapped.setFlightNumber("WA100");

        when(flightRepository.existsByFlightNumber("WA100")).thenReturn(false);
        when(airportRepository.findByIataCode("IST")).thenReturn(Optional.of(departure));
        when(airportRepository.findByIataCode("JFK")).thenReturn(Optional.of(arrival));
        when(flightMapper.toEntity(request)).thenReturn(mapped);
        when(flightRepository.saveAndFlush(any(Flight.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flightMapper.toResponse(any(Flight.class))).thenReturn(mock(FlightResponse.class));

        service.create(request);

        ArgumentCaptor<Flight> captor = ArgumentCaptor.forClass(Flight.class);
        verify(flightRepository).saveAndFlush(captor.capture());
        Flight saved = captor.getValue();
        assertThat(saved.getDepartureAirport()).isEqualTo(departure);
        assertThat(saved.getArrivalAirport()).isEqualTo(arrival);
        assertThat(saved.getStatus()).isEqualTo(FlightStatus.SCHEDULED);

        verify(eventPublisher).publishFlightCreated(any(FlightCreatedEvent.class));
    }

    @Test
    void create_lowercaseCodesNormalizedForLookupAndSave() {
        FlightRequest request = buildRequest("wa100", "ist", "jfk");
        Airport departure = buildAirportIST();
        Airport arrival = buildAirportJFK();
        Flight mapped = new Flight();
        mapped.setFlightNumber("wa100");

        when(flightRepository.existsByFlightNumber("WA100")).thenReturn(false);
        when(airportRepository.findByIataCode("IST")).thenReturn(Optional.of(departure));
        when(airportRepository.findByIataCode("JFK")).thenReturn(Optional.of(arrival));
        when(flightMapper.toEntity(request)).thenReturn(mapped);
        when(flightRepository.saveAndFlush(any(Flight.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flightMapper.toResponse(any(Flight.class))).thenReturn(mock(FlightResponse.class));

        FlightResponse response = service.create(request);

        ArgumentCaptor<Flight> captor = ArgumentCaptor.forClass(Flight.class);
        verify(flightRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getFlightNumber()).isEqualTo("WA100");
    }

    @Test
    void create_pastDepartureTime_throwsIllegalArgumentException() {
        FlightRequest request = new FlightRequest("WA100", "IST", "JFK", "Waylo Air",
                Instant.parse("2026-10-31T00:00:00Z"), Instant.parse("2026-10-31T02:00:00Z"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Departure time must be in the future");
        verify(flightRepository, never()).existsByFlightNumber(any());
    }

    @Test
    void create_sameAirports_throwsIllegalArgumentException() {
        // Same departure/arrival times → validateSchedule catches it
        FlightRequest request = new FlightRequest("WA100", "IST", "IST", "Waylo Air",
                Instant.parse("2026-12-01T07:00:00Z"), Instant.parse("2026-12-01T07:00:00Z"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Arrival time must be after departure time");
        verify(flightRepository, never()).existsByFlightNumber(any());
    }

    @Test
    void create_arrivalBeforeDeparture_throwsIllegalArgumentException() {
        FlightRequest request = new FlightRequest("WA100", "IST", "JFK", "Waylo Air",
                Instant.parse("2026-12-01T10:00:00Z"), Instant.parse("2026-12-01T08:00:00Z"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Arrival time must be after departure time");
    }

    @Test
    void create_durationExceeds20Hours_throwsIllegalArgumentException() {
        FlightRequest request = new FlightRequest("WA100", "IST", "JFK", "Waylo Air",
                Instant.parse("2026-12-01T00:00:00Z"), Instant.parse("2026-12-01T21:00:00Z"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Flight duration cannot exceed 20 hours");
    }

    @Test
    void create_invalidFlightNumberFormat_throwsIllegalArgumentException() {
        FlightRequest request = buildRequest("X", "IST", "JFK");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid flight number format");
    }

    // ===================== getById =====================

    @Test
    void getById_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(flightRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_found_returnsMapped() {
        UUID id = UUID.randomUUID();
        Flight flight = new Flight();
        FlightResponse response = mock(FlightResponse.class);
        when(flightRepository.findById(id)).thenReturn(Optional.of(flight));
        when(flightMapper.toResponse(flight)).thenReturn(response);

        assertThat(service.getById(id)).isEqualTo(response);
    }

    // ===================== getByFlightNumber =====================

    @Test
    void getByFlightNumber_notFound_throwsResourceNotFound() {
        when(flightRepository.findByFlightNumber("WA100")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByFlightNumber("wa100"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByFlightNumber_found_normalizesCode() {
        Flight flight = new Flight();
        FlightResponse response = mock(FlightResponse.class);
        when(flightRepository.findByFlightNumber("WA100")).thenReturn(Optional.of(flight));
        when(flightMapper.toResponse(flight)).thenReturn(response);

        assertThat(service.getByFlightNumber("wa100")).isEqualTo(response);
    }

    // ===================== list =====================

    @SuppressWarnings("unchecked")
    private Specification<Flight> captureSpec(ArgumentCaptor<Pageable> pageable) {
        ArgumentCaptor<Specification<Flight>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(flightRepository).findAll(spec.capture(), pageable.capture());
        return spec.getValue();
    }

    @SuppressWarnings("unchecked")
    private void stubEmptyPage() {
        when(flightRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
    }

    @Test
    void list_pagesSortedByDepartureAscAndMapsContent() {
        Flight f1 = buildFlight("WA1", FlightStatus.SCHEDULED,
                Instant.parse("2026-12-01T10:00:00Z"), Instant.parse("2026-12-01T12:00:00Z"));
        FlightResponse r = mock(FlightResponse.class);
        when(flightRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(f1), PageRequest.of(2, 50), 101));
        when(flightMapper.toResponse(f1)).thenReturn(r);

        Page<FlightResponse> result = service.list(null, null, null, false, null, 2, 50);

        assertThat(result.getContent()).containsExactly(r);
        assertThat(result.getTotalElements()).isEqualTo(101);
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        captureSpec(p);
        assertThat(p.getValue().getPageNumber()).isEqualTo(2);
        assertThat(p.getValue().getPageSize()).isEqualTo(50);
        assertThat(p.getValue().getSort().getOrderFor("departureTime").isAscending()).isTrue();
    }

    @Test
    void list_sizeAboveMaxIsClampedTo200() {
        stubEmptyPage();
        service.list(null, null, null, false, null, 0, 5000);
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        captureSpec(p);
        assertThat(p.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void list_nonPositiveSizeAndNegativePageAreNormalised() {
        stubEmptyPage();
        service.list(null, null, null, false, null, -3, 0);
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        captureSpec(p);
        assertThat(p.getValue().getPageNumber()).isZero();
        assertThat(p.getValue().getPageSize()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private CriteriaBuilder runSpec(Specification<Flight> spec) {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Root<Flight> root = mock(Root.class);
        Path<Object> path = mock(Path.class);
        // lenient: a spec with no active filter never reads an attribute, which is exactly what some tests assert.
        lenient().when(root.get(anyString())).thenReturn(path);
        spec.toPredicate(root, mock(CriteriaQuery.class), cb);
        return cb;
    }

    @Test
    void list_upcomingFiltersDepartureFromNow() {
        stubEmptyPage();
        service.list(null, null, null, true, null, 0, 50);
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        CriteriaBuilder cb = runSpec(captureSpec(p));
        verify(cb).greaterThanOrEqualTo(any(Expression.class), eq(FIXED_NOW));
        verify(cb, never()).equal(any(Expression.class), any(Object.class));
    }

    @Test
    void list_dateRangeUsesIstanbulDaysInclusive() {
        stubEmptyPage();
        service.list(LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-02"), null, false, null, 0, 50);
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        CriteriaBuilder cb = runSpec(captureSpec(p));
        // Istanbul is UTC+3: 2026-12-01 00:00 local = 2026-11-30T21:00Z, end exclusive 2026-12-03 00:00 local
        verify(cb).greaterThanOrEqualTo(any(Expression.class), eq(Instant.parse("2026-11-30T21:00:00Z")));
        verify(cb).lessThan(any(Expression.class), eq(Instant.parse("2026-12-02T21:00:00Z")));
    }

    @Test
    void list_statusFilterAddsEquality() {
        stubEmptyPage();
        service.list(null, null, FlightStatus.CANCELLED, false, null, 0, 50);
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        CriteriaBuilder cb = runSpec(captureSpec(p));
        verify(cb).equal(any(Expression.class), eq(FlightStatus.CANCELLED));
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_flightNumberFilterIsContainsUpperCasedAndStripped() {
        stubEmptyPage();
        service.list(null, null, null, true, " wd-1%_ ", 0, 200);
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        CriteriaBuilder cb = runSpec(captureSpec(p));
        // Only letters and digits are kept, so user input can never inject LIKE wildcards.
        verify(cb).like(any(Expression.class), eq("%WD1%"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_blankOrSymbolOnlyFlightNumberAddsNoFilter() {
        stubEmptyPage();
        service.list(null, null, null, false, "  -%  ", 0, 50);
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        CriteriaBuilder cb = runSpec(captureSpec(p));
        verify(cb, never()).like(any(Expression.class), anyString());
    }

    // ===================== search =====================

    @Test
    void search_sameAirport_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.search("IST", "IST", null, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Departure and arrival airports must differ");
    }

    @Test
    void search_blankFrom_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.search("  ", "JFK", null, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("from and to are required");
    }

    @Test
    void search_blankTo_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.search("IST", "   ", null, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("from and to are required");
    }

    @Test
    void search_bothDateAndRange_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.search("IST", "JFK", LocalDate.of(2026, 12, 15),
                Instant.parse("2026-12-15T00:00:00Z"), Instant.parse("2026-12-16T00:00:00Z"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Use either date or departureFrom/departureTo, not both");
    }

    @Test
    void search_neitherDateNorRange_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.search("IST", "JFK", null, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Either date or departureFrom/departureTo is required");
    }

    @Test
    void search_partialDepartureFrom_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.search("IST", "JFK", null,
                Instant.parse("2026-12-15T00:00:00Z"), null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Both departureFrom and departureTo are required, or neither");
    }

    @Test
    void search_partialDepartureTo_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.search("IST", "JFK", null,
                null, Instant.parse("2026-12-16T00:00:00Z"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Both departureFrom and departureTo are required, or neither");
    }

    @Test
    void search_dateWithPartialRange_throwsIllegalArgumentException() {
        // date + partial range → partial check fires first (usingRangeFrom != usingRangeTo)
        assertThatThrownBy(() -> service.search("IST", "JFK", LocalDate.of(2026, 12, 15),
                Instant.parse("2026-12-15T00:00:00Z"), null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Both departureFrom and departureTo are required, or neither");
    }

    @Test
    void search_departureFromNotBeforeDepartureTo_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.search("IST", "JFK", null,
                Instant.parse("2026-12-02T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("departureFrom must be before departureTo");
    }

    @Test
    void search_rangeOver31Days_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.search("IST", "JFK", null,
                Instant.parse("2026-12-01T00:00:00Z"), Instant.parse("2027-01-10T00:00:00Z"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Search range must be at most 31 days");
    }

    @Test
    void search_range31DaysPlusOneHour_throwsIllegalArgumentException() {
        // Exactly 31 days + 1 hour > 31 days boundary (Dec 1 to Jan 1 01:00 = 31d+1h)
        assertThatThrownBy(() -> service.search("IST", "JFK", null,
                Instant.parse("2026-12-01T00:00:00Z"), Instant.parse("2027-01-01T01:00:00Z"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Search range must be at most 31 days");
    }

    @Test
    void search_dateMode_passesCorrectIstanbulStartEnd() {
        Airport ist = buildAirportIST();
        when(airportRepository.findByIataCode("IST")).thenReturn(Optional.of(ist));
        when(flightRepository.searchSchedule(eq("IST"), eq("JFK"), any(Instant.class), any(Instant.class), eq(false), eq(FlightStatus.CANCELLED)))
                .thenAnswer(inv -> {
                    Instant start = inv.getArgument(2);
                    Instant end = inv.getArgument(3);
                    // Europe/Istanbul UTC+3 in December
                    // 2026-12-15 00:00 Istanbul = 2026-12-14 21:00 UTC
                    // 2026-12-16 00:00 Istanbul = 2026-12-15 21:00 UTC
                    Instant expectedStart = Instant.parse("2026-12-14T21:00:00Z");
                    Instant expectedEnd = Instant.parse("2026-12-15T21:00:00Z");
                    assertThat(start).isEqualTo(expectedStart);
                    assertThat(end).isEqualTo(expectedEnd);
                    return List.of();
                });

        service.search("IST", "JFK", LocalDate.of(2026, 12, 15), null, null, false);
    }

    @Test
    void search_rangeMode_passesThroughIncludeCancelled() {
        when(flightRepository.searchSchedule(eq("IST"), eq("JFK"), any(Instant.class), any(Instant.class), eq(true), eq(FlightStatus.CANCELLED)))
                .thenAnswer(inv -> {
                    boolean includeCancelled = inv.getArgument(4);
                    assertThat(includeCancelled).isTrue();
                    return List.of();
                });

        service.search("IST", "JFK", null,
                Instant.parse("2026-12-15T00:00:00Z"), Instant.parse("2026-12-16T00:00:00Z"), true);
    }

    @Test
    void search_dateMode_lowercaseNormalized() {
        Airport ist = buildAirportIST();
        Airport jfk = buildAirportJFK();
        when(airportRepository.findByIataCode("IST")).thenReturn(Optional.of(ist));
        when(flightRepository.searchSchedule(eq("IST"), eq("JFK"), any(Instant.class), any(Instant.class), eq(false), eq(FlightStatus.CANCELLED)))
                .thenReturn(List.of());

        service.search("ist", "jfk", LocalDate.of(2026, 12, 15), null, null, false);

        verify(flightRepository).searchSchedule(eq("IST"), eq("JFK"), any(Instant.class), any(Instant.class), eq(false), eq(FlightStatus.CANCELLED));
    }

    // ===================== update =====================

    @Test
    void update_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(flightRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, buildRequest("WA1", "IST", "JFK")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_cancelledFlight_throwsFLIGHT_CANCELLED() {
        UUID id = UUID.randomUUID();
        Flight cancelled = buildFlight("WA1", FlightStatus.CANCELLED,
                Instant.parse("2026-12-01T10:00:00Z"), Instant.parse("2026-12-01T12:00:00Z"));
        when(flightRepository.findById(id)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.update(id, buildRequest("WA1", "IST", "JFK")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Cannot update a cancelled flight")
                .extracting("ruleCode").isEqualTo("FLIGHT_CANCELLED");

        verify(flightRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_departedFlight_throwsFLIGHT_DEPARTED() {
        UUID id = UUID.randomUUID();
        Flight departed = buildFlight("WA1", FlightStatus.SCHEDULED,
                Instant.parse("2026-10-31T00:00:00Z"), Instant.parse("2026-10-31T02:00:00Z"));
        when(flightRepository.findById(id)).thenReturn(Optional.of(departed));

        assertThatThrownBy(() -> service.update(id, buildRequest("WA1", "IST", "JFK")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Cannot update a flight that has already departed")
                .extracting("ruleCode").isEqualTo("FLIGHT_DEPARTED");
    }

    @Test
    void update_flightNumberChangedToTaken_throwsDuplicateResourceException() {
        UUID id = UUID.randomUUID();
        Flight existing = buildFlight("WA1", FlightStatus.SCHEDULED,
                Instant.parse("2026-12-01T10:00:00Z"), Instant.parse("2026-12-01T12:00:00Z"));
        when(flightRepository.findById(id)).thenReturn(Optional.of(existing));
        when(flightRepository.existsByFlightNumber("WA999")).thenReturn(true);

        FlightRequest request = new FlightRequest("WA999", "IST", "JFK", "Waylo Air",
                Instant.parse("2026-12-01T10:00:00Z"), Instant.parse("2026-12-01T12:00:00Z"));

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void update_valid_reassignsAirportsAndSaves() {
        UUID id = UUID.randomUUID();
        Flight existing = buildFlight("WA1", FlightStatus.SCHEDULED,
                Instant.parse("2026-12-01T10:00:00Z"), Instant.parse("2026-12-01T12:00:00Z"));
        Airport newDep = buildAirport("LHR", "Europe/London");
        Airport newArr = buildAirport("CDG", "Europe/Paris");
        FlightRequest request = new FlightRequest("WA1", "LHR", "CDG", "Waylo Air",
                Instant.parse("2026-12-01T10:00:00Z"), Instant.parse("2026-12-01T12:00:00Z"));

        when(flightRepository.findById(id)).thenReturn(Optional.of(existing));
        when(airportRepository.findByIataCode("LHR")).thenReturn(Optional.of(newDep));
        when(airportRepository.findByIataCode("CDG")).thenReturn(Optional.of(newArr));
        when(flightRepository.saveAndFlush(existing)).thenReturn(existing);
        when(flightMapper.toResponse(existing)).thenReturn(mock(FlightResponse.class));

        service.update(id, request);

        verify(flightMapper).updateEntity(request, existing);
        assertThat(existing.getDepartureAirport()).isEqualTo(newDep);
        assertThat(existing.getArrivalAirport()).isEqualTo(newArr);
    }

    @Test
    void update_lowercaseFlightNumberNormalized_afterMapper() {
        UUID id = UUID.randomUUID();
        Flight existing = buildFlight("WA1", FlightStatus.SCHEDULED,
                Instant.parse("2026-12-01T10:00:00Z"), Instant.parse("2026-12-01T12:00:00Z"));
        Airport ist = buildAirportIST();
        Airport jfk = buildAirportJFK();

        when(flightRepository.findById(id)).thenReturn(Optional.of(existing));
        when(flightRepository.existsByFlightNumber("TK123")).thenReturn(false);
        when(airportRepository.findByIataCode("IST")).thenReturn(Optional.of(ist));
        when(airportRepository.findByIataCode("JFK")).thenReturn(Optional.of(jfk));
        when(flightRepository.saveAndFlush(existing)).thenReturn(existing);
        when(flightMapper.toResponse(existing)).thenReturn(mock(FlightResponse.class));

        // Request with lowercase and padded flight number " tk123 "
        FlightRequest request = new FlightRequest(" tk123 ", "IST", "JFK", "Waylo Air",
                Instant.parse("2026-12-01T10:00:00Z"), Instant.parse("2026-12-01T12:00:00Z"));

        service.update(id, request);

        // verify the saved entity has normalized flight number (mapper does NOT overwrite)
        assertThat(existing.getFlightNumber()).isEqualTo("TK123");
        verify(flightMapper).updateEntity(request, existing);
    }

    @Test
    void update_movingDepartureIntoPast_throwsIllegalArgumentException() {
        UUID id = UUID.randomUUID();
        Flight existing = buildFlight("WA1", FlightStatus.SCHEDULED,
                Instant.parse("2026-12-01T10:00:00Z"), Instant.parse("2026-12-01T12:00:00Z"));

        when(flightRepository.findById(id)).thenReturn(Optional.of(existing));

        // Request with departure in the past (FIXED_NOW is 2026-11-01T00:00:00Z)
        FlightRequest request = new FlightRequest("WA1", "IST", "JFK", "Waylo Air",
                Instant.parse("2026-10-31T00:00:00Z"), Instant.parse("2026-10-31T02:00:00Z"));

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Departure time must be in the future");

        verify(flightRepository, never()).saveAndFlush(any());
    }

    // ===================== delete =====================

    @Test
    void delete_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(flightRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_scheduled_throwsFLIGHT_NOT_CANCELLED() {
        UUID id = UUID.randomUUID();
        Flight scheduled = buildFlight("WA1", FlightStatus.SCHEDULED,
                Instant.parse("2026-12-01T10:00:00Z"), Instant.parse("2026-12-01T12:00:00Z"));
        when(flightRepository.findById(id)).thenReturn(Optional.of(scheduled));

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Cancel the flight before deleting it")
                .extracting("ruleCode").isEqualTo("FLIGHT_NOT_CANCELLED");

        verify(flightRepository, never()).delete(any(Flight.class));
    }

    @Test
    void delete_cancelled_deletes() {
        UUID id = UUID.randomUUID();
        Flight cancelled = buildFlight("WA1", FlightStatus.CANCELLED,
                Instant.parse("2026-10-31T00:00:00Z"), Instant.parse("2026-10-31T02:00:00Z"));
        when(flightRepository.findById(id)).thenReturn(Optional.of(cancelled));

        service.delete(id);

        verify(flightRepository).delete(cancelled);
    }

    // ===================== cancel =====================

    @Test
    void cancel_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(flightRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(id)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void cancel_alreadyCancelled_throwsFLIGHT_ALREADY_CANCELLED() {
        UUID id = UUID.randomUUID();
        Flight cancelled = buildFlight("WA1", FlightStatus.CANCELLED,
                Instant.parse("2026-12-01T10:00:00Z"), Instant.parse("2026-12-01T12:00:00Z"));
        when(flightRepository.findById(id)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.cancel(id))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Flight is already cancelled")
                .extracting("ruleCode").isEqualTo("FLIGHT_ALREADY_CANCELLED");

        // Verify publisher never called
        verify(eventPublisher, never()).publishFlightCancelled(any(FlightCancelledEvent.class));
        // Verify save never called
        verify(flightRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancel_departed_throwsFLIGHT_DEPARTED() {
        UUID id = UUID.randomUUID();
        Flight departed = buildFlight("WA1", FlightStatus.SCHEDULED,
                Instant.parse("2026-10-31T00:00:00Z"), Instant.parse("2026-10-31T02:00:00Z"));
        when(flightRepository.findById(id)).thenReturn(Optional.of(departed));

        assertThatThrownBy(() -> service.cancel(id))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Cannot cancel a flight that has already departed")
                .extracting("ruleCode").isEqualTo("FLIGHT_DEPARTED");

        verify(eventPublisher, never()).publishFlightCancelled(any());
    }

    @Test
    void cancel_valid_setsStatusCancelledAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        Flight flight = buildFlight("WA300", FlightStatus.SCHEDULED,
                Instant.parse("2026-12-01T10:00:00Z"), Instant.parse("2026-12-01T12:00:00Z"));
        when(flightRepository.findById(id)).thenReturn(Optional.of(flight));
        when(flightRepository.saveAndFlush(flight)).thenReturn(flight);
        when(flightMapper.toResponse(flight)).thenReturn(mock(FlightResponse.class));

        service.cancel(id);

        assertThat(flight.getStatus()).isEqualTo(FlightStatus.CANCELLED);
        verify(eventPublisher).publishFlightCancelled(any(FlightCancelledEvent.class));
    }
}
