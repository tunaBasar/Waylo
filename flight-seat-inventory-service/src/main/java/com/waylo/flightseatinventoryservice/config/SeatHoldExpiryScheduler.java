package com.waylo.flightseatinventoryservice.config;

import com.waylo.flightseatinventoryservice.repository.FlightSeatRepository;
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

    public SeatHoldExpiryScheduler(FlightSeatRepository flightSeatRepository) {
        this.flightSeatRepository = flightSeatRepository;
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void releaseExpiredHolds() {
        int released = flightSeatRepository.releaseExpiredHolds(Instant.now());
        log.info("Released {} expired seat holds", released);
    }
}
