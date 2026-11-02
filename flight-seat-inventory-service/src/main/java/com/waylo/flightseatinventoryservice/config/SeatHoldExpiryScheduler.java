package com.waylo.flightseatinventoryservice.config;

import com.waylo.flightseatinventoryservice.repository.FlightSeatRepository;
import com.waylo.flightseatinventoryservice.supplier.InternalFlightSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class SeatHoldExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SeatHoldExpiryScheduler.class);

    private final FlightSeatRepository flightSeatRepository;
    private final InternalFlightSupplier supplier;

    public SeatHoldExpiryScheduler(FlightSeatRepository flightSeatRepository, InternalFlightSupplier supplier) {
        this.flightSeatRepository = flightSeatRepository;
        this.supplier = supplier;
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void releaseExpiredHolds() {
        int expiredHolds = supplier.expireHolds();
        int released = flightSeatRepository.releaseExpiredHolds(Instant.now());
        log.info("Expired {} order holds, released {} expired seat holds", expiredHolds, released);
    }
}
