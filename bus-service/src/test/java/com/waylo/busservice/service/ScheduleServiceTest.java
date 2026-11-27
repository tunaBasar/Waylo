package com.waylo.busservice.service;

import com.waylo.busservice.client.AuthGateway;
import com.waylo.busservice.domain.*;
import com.waylo.busservice.dto.BusDtos.*;
import com.waylo.busservice.exception.BusinessRuleException;
import com.waylo.busservice.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ScheduleServiceTest {

    private final AuthGateway auth = mock(AuthGateway.class);
    private final BusService busService = mock(BusService.class);
    private final BusRepository buses = mock(BusRepository.class);
    private final TerminalRepository terminals = mock(TerminalRepository.class);
    private final TripRepository trips = mock(TripRepository.class);
    private final TripScheduleRepository schedules = mock(TripScheduleRepository.class);
    private final TripScheduleStopRepository stops = mock(TripScheduleStopRepository.class);
    private final TripScheduleFareRepository fares = mock(TripScheduleFareRepository.class);
    // Friday 2027-02-05 10:00 Istanbul
    private final Clock clock = Clock.fixed(Instant.parse("2027-02-05T07:00:00Z"), ZoneOffset.UTC);

    private final UUID orgId = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final UUID t1 = UUID.randomUUID();
    private final UUID t2 = UUID.randomUUID();
    private Bus bus;
    private ScheduleService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleService(auth, clock, busService, buses, terminals, trips, schedules, stops, fares);
        bus = new Bus();
        bus.id = UUID.randomUUID();
        bus.organizationId = orgId;
        bus.active = true;
        when(buses.findById(bus.id)).thenReturn(Optional.of(bus));
        when(terminals.findAllById(any())).thenAnswer(i -> List.of(terminal(t1, true), terminal(t2, true)));
        when(schedules.findByOrganizationIdAndCode(any(), any())).thenReturn(Optional.empty());
    }

    private Terminal terminal(UUID id, boolean active) {
        Terminal t = new Terminal();
        t.id = id;
        t.active = active;
        return t;
    }

    private ScheduleRequest req(List<DayOfWeek> days, int horizon, List<ScheduleStopItem> s, List<FareItem> f) {
        return new ScheduleRequest(bus.id, "KPD-101", "TRY", days, LocalTime.of(22, 30), LocalDate.of(2027, 2, 1), null,
                horizon, s, f, null);
    }

    private List<ScheduleStopItem> okStops() {
        return List.of(new ScheduleStopItem(t1, 0, 0), new ScheduleStopItem(t2, 300, 300));
    }

    private List<FareItem> okFares() { return List.of(new FareItem(0, 1, new BigDecimal("400"))); }

    private String code(ScheduleRequest r) {
        return ((BusinessRuleException) org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleException.class,
                () -> service.create(user, orgId, r))).getRuleCode();
    }

    @Test
    void validationCodes() {
        assertThat(code(req(List.of(), 30, okStops(), okFares()))).isEqualTo("INVALID_SCHEDULE");
        assertThat(code(req(List.of(DayOfWeek.FRIDAY, DayOfWeek.FRIDAY), 30, okStops(), okFares()))).isEqualTo("INVALID_SCHEDULE");
        assertThat(code(req(List.of(DayOfWeek.FRIDAY), 121, okStops(), okFares()))).isEqualTo("INVALID_SCHEDULE");
        assertThat(code(req(List.of(DayOfWeek.FRIDAY), 30,
                List.of(new ScheduleStopItem(t1, 0, 0), new ScheduleStopItem(t2, 0, 10)), okFares()))).isEqualTo("INVALID_SCHEDULE");
        assertThat(code(req(List.of(DayOfWeek.FRIDAY), 30,
                List.of(new ScheduleStopItem(t1, 0, 0), new ScheduleStopItem(t2, 5000, 5000)), okFares()))).isEqualTo("INVALID_SCHEDULE");
        assertThat(code(req(List.of(DayOfWeek.FRIDAY), 30, List.of(new ScheduleStopItem(t1, 0, 0)), okFares()))).isEqualTo("INVALID_STOPS");
        assertThat(code(req(List.of(DayOfWeek.FRIDAY), 30, okStops(), List.of(new FareItem(0, 5, BigDecimal.ONE))))).isEqualTo("INVALID_FARES");
        verify(schedules, never()).saveAndFlush(any());
    }

    @Test
    void validToBeforeValidFromIsInvalid() {
        ScheduleRequest r = new ScheduleRequest(bus.id, "X", "TRY", List.of(DayOfWeek.FRIDAY), LocalTime.of(8, 0),
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 2, 1), 30, okStops(), okFares(), null);
        assertThat(code(r)).isEqualTo("INVALID_SCHEDULE");
    }

    @Test
    void duplicateCodeInactiveBusAndInactiveTerminal() {
        when(schedules.findByOrganizationIdAndCode(orgId, "KPD-101")).thenReturn(Optional.of(new TripSchedule()));
        assertThat(code(req(List.of(DayOfWeek.FRIDAY), 30, okStops(), okFares()))).isEqualTo("SCHEDULE_CODE_TAKEN");
        when(schedules.findByOrganizationIdAndCode(any(), any())).thenReturn(Optional.empty());
        bus.active = false;
        assertThat(code(req(List.of(DayOfWeek.FRIDAY), 30, okStops(), okFares()))).isEqualTo("BUS_INACTIVE");
        bus.active = true;
        when(terminals.findAllById(any())).thenReturn(List.of(terminal(t1, true), terminal(t2, false)));
        assertThat(code(req(List.of(DayOfWeek.FRIDAY), 30, okStops(), okFares()))).isEqualTo("INVALID_TERMINAL");
    }

    @Test
    void updateReplacesTimetableButNeverTouchesTrips() {
        TripSchedule s = new TripSchedule();
        s.id = UUID.randomUUID();
        s.organizationId = orgId;
        s.version = 3L;
        when(schedules.findById(s.id)).thenReturn(Optional.of(s));
        service.update(user, orgId, s.id, req(List.of(DayOfWeek.MONDAY), 30, okStops(), okFares()));
        assertThat(s.weekdays).isEqualTo((short) 1);
        verify(stops).deleteAllByScheduleId(s.id);
        verify(fares).deleteAllByScheduleId(s.id);
        verifyNoInteractions(trips);
        verifyNoInteractions(busService);
    }

    @Test
    void applyOneSkipsInactiveBusAndCreatesNothing() {
        TripSchedule s = new TripSchedule();
        s.id = UUID.randomUUID();
        s.organizationId = orgId;
        s.busId = bus.id;
        s.active = true;
        bus.active = false;
        when(schedules.findById(s.id)).thenReturn(Optional.of(s));
        assertThat(service.applyOne(s.id)).isEqualTo(ScheduleSummary.ZERO);
        verifyNoInteractions(busService);
    }

    @Test
    void applyOneCreatesTripsThroughSharedPathWithScheduleId() {
        TripSchedule s = new TripSchedule();
        s.id = UUID.randomUUID();
        s.organizationId = orgId;
        s.busId = bus.id;
        s.code = "KPD-101";
        s.currency = "TRY";
        s.active = true;
        s.weekdays = (short) 127;
        s.departureTime = LocalTime.of(22, 30);
        s.validFrom = LocalDate.of(2027, 2, 1);
        s.horizonDays = 2;
        when(schedules.findById(s.id)).thenReturn(Optional.of(s));
        TripScheduleStop a = new TripScheduleStop();
        a.terminalId = t1;
        TripScheduleStop b = new TripScheduleStop();
        b.terminalId = t2;
        b.arrivalOffsetMinutes = 300;
        b.departureOffsetMinutes = 300;
        when(stops.findByScheduleIdOrderBySeq(s.id)).thenReturn(List.of(a, b));
        TripScheduleFare f = new TripScheduleFare();
        f.fromSeq = 0;
        f.toSeq = 1;
        f.price = new BigDecimal("400");
        when(fares.findByScheduleId(s.id)).thenReturn(List.of(f));
        ScheduleSummary r = service.applyOne(s.id);
        assertThat(r.created()).isEqualTo(2);
        verify(busService, times(2)).createTripFrom(eq(orgId), eq(bus), eq("KPD-101"), eq("TRY"), any(), any(), eq(s.id));
    }
}
