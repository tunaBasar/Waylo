package com.waylo.busservice.rules;

import com.waylo.busservice.domain.Bus;
import com.waylo.busservice.domain.Gender;
import com.waylo.busservice.domain.Ticket;
import com.waylo.busservice.dto.BusDtos.PassengerItem;
import com.waylo.busservice.exception.BusinessRuleException;
import com.waylo.busservice.exception.ConflictException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Pure seat rules: segment overlap, seat existence, neighbour gender rule. */
public final class SeatRules {

    private SeatRules() { }

    /** Segments are half open [from, to): a ticket 0->2 and one 2->4 do not overlap. */
    public static boolean overlaps(int aFrom, int aTo, int bFrom, int bTo) {
        return aFrom < bTo && bFrom < aTo;
    }

    public static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_EVEN); }

    /**
     * Validates the requested passengers against the bus plan and the blocking tickets of the trip.
     * Throws 422 INVALID_SEAT / DUPLICATE_SEAT / SEAT_GENDER_CONFLICT, 409 SEAT_TAKEN.
     */
    public static void validate(Bus bus, int fromSeq, int toSeq, List<PassengerItem> passengers, List<Ticket> blocking) {
        Set<Integer> requested = new HashSet<>();
        for (PassengerItem p : passengers) {
            if (p.seatNo() < 1 || p.seatNo() > bus.seatCount) {
                throw new BusinessRuleException("INVALID_SEAT", "Seat " + p.seatNo() + " does not exist on this bus");
            }
            if (!requested.add(p.seatNo())) {
                throw new BusinessRuleException("DUPLICATE_SEAT", "Seat " + p.seatNo() + " was requested twice");
            }
        }
        Map<Integer, Gender> occupiedNeighbors = new HashMap<>();
        for (Ticket t : blocking) {
            if (!overlaps(fromSeq, toSeq, t.fromSeq, t.toSeq)) continue;
            if (requested.contains(t.seatNo)) {
                throw new ConflictException("SEAT_TAKEN", "Seat " + t.seatNo + " is no longer available");
            }
            occupiedNeighbors.put(t.seatNo, t.gender);
        }
        Map<Integer, Gender> own = new HashMap<>();
        passengers.forEach(p -> own.put(p.seatNo(), p.gender()));
        for (PassengerItem p : passengers) {
            Optional<Integer> neighbor = bus.layout.neighbor(p.seatNo());
            if (neighbor.isEmpty()) continue;
            Gender other = occupiedNeighbors.get(neighbor.get());
            if (other != null && other != p.gender() && !own.containsKey(neighbor.get())) {
                throw new BusinessRuleException("SEAT_GENDER_CONFLICT",
                        "Seat " + p.seatNo() + " is next to a passenger of the other gender");
            }
        }
    }

    /** Seats still free for the segment, with the neighbour's gender for the seat map UI. */
    public static Map<Integer, Gender> occupied(int fromSeq, int toSeq, List<Ticket> blocking) {
        Map<Integer, Gender> map = new HashMap<>();
        for (Ticket t : blocking) {
            if (overlaps(fromSeq, toSeq, t.fromSeq, t.toSeq)) map.put(t.seatNo, t.gender);
        }
        return map;
    }
}
