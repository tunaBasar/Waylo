package com.waylo.hotelservice.service;

import com.waylo.hotelservice.domain.DailyInventory;
import com.waylo.hotelservice.domain.HoldStatus;
import com.waylo.hotelservice.domain.HotelHold;
import com.waylo.hotelservice.domain.HotelQuote;
import com.waylo.hotelservice.dto.HotelHoldRequest;
import com.waylo.hotelservice.dto.HotelHoldResponse;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.DailyInventoryRepository;
import com.waylo.hotelservice.repository.HotelHoldRepository;
import com.waylo.hotelservice.repository.HotelQuoteRepository;
import com.waylo.hotelservice.repository.HotelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of hotel hold operations.
 * Holds reserve inventory for a quote within a time window.
 */
@Service
public class HotelHoldServiceImpl implements HotelHoldService {

    private static final Logger log = LoggerFactory.getLogger(HotelHoldServiceImpl.class);

    private final HotelQuoteRepository quoteRepository;
    private final HotelHoldRepository holdRepository;
    private final DailyInventoryRepository inventoryRepository;
    private final HotelRepository hotelRepository;
    private final Clock clock;
    private final int holdTtlMinutes;

    public HotelHoldServiceImpl(HotelQuoteRepository quoteRepository,
                                HotelHoldRepository holdRepository,
                                DailyInventoryRepository inventoryRepository,
                                HotelRepository hotelRepository,
                                Clock clock,
                                @Value("${waylo.hold.ttl-minutes:15}") int holdTtlMinutes) {
        this.quoteRepository = quoteRepository;
        this.holdRepository = holdRepository;
        this.inventoryRepository = inventoryRepository;
        this.hotelRepository = hotelRepository;
        this.clock = clock;
        this.holdTtlMinutes = holdTtlMinutes;
    }

    @Override
    @Transactional
    public HotelHoldResponse hold(HotelHoldRequest request) {
        Instant now = Instant.now(clock);

        // 1. Load quote with nights
        HotelQuote quote = quoteRepository.findByIdWithNights(request.quoteId())
                .orElseThrow(() -> new ResourceNotFoundException("Quote", "id", request.quoteId()));

        // 2. Quote expiry check (inclusive: expiresAt == now means expired)
        if (quote.isExpired(clock)) {
            throw new BusinessRuleException("QUOTE_EXPIRED",
                    "Quote " + request.quoteId() + " has expired");
        }

        // 3. Principal user must match quote owner
        if (request.principalUserId() == null) {
            throw new BusinessRuleException("MISSING_PRINCIPAL",
                    "principalUserId is required for hold creation");
        }
        if (!request.principalUserId().equals(quote.getPrincipalUserId())) {
            throw new BusinessRuleException("PRINCIPAL_MISMATCH",
                    "Hold principal " + request.principalUserId() + " does not match quote owner " + quote.getPrincipalUserId());
        }

        // 4. Idempotency pre-check: return existing hold with same key and matching quote+principal, or reject conflict
        Optional<HotelHold> existing = holdRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            HotelHold hold = existing.get();
            if (hold.getQuoteId().equals(request.quoteId()) &&
                hold.getPrincipalUserId().equals(request.principalUserId())) {
                return toResponse(hold);
            }
            throw new BusinessRuleException("IDEMPOTENCY_CONFLICT",
                    "Idempotency key already used for quote " + hold.getQuoteId() + " (request is for " + request.quoteId() + ")");
        }

        // 5. Lock quote to prevent concurrent duplicate holds
        HotelQuote lockedQuote = quoteRepository.findByIdForUpdate(request.quoteId())
                .orElseThrow(() -> new ResourceNotFoundException("Quote", "id", request.quoteId()));

        // 6. Recheck idempotency after quote lock (only HELD can be returned/rejected here)
        Optional<HotelHold> postLockHold = holdRepository.findByIdempotencyKey(request.idempotencyKey());
        if (postLockHold.isPresent()) {
            HotelHold hold = postLockHold.get();
            if (hold.getStatus() == HoldStatus.HELD) {
                if (hold.getQuoteId().equals(request.quoteId()) &&
                    hold.getPrincipalUserId().equals(request.principalUserId())) {
                    return toResponse(hold);
                }
                throw new BusinessRuleException("IDEMPOTENCY_CONFLICT",
                        "Idempotency key already used for a different quote");
            }
        }

        // 7. Check no existing HELD or CONFIRMED hold for this quote
        List<HoldStatus> activeStatuses = List.of(HoldStatus.HELD, HoldStatus.CONFIRMED);
        long activeCount = holdRepository.countByQuoteIdAndStatusIn(request.quoteId(), activeStatuses);
        if (activeCount > 0) {
            throw new BusinessRuleException("QUOTE_ALREADY_HELD",
                    "Quote " + request.quoteId() + " already has an active hold");
        }

        // 8. Recheck quote expiry after acquiring quote lock (concurrent release could have expired it)
        Instant nowAfterLock = Instant.now(clock);
        if (!nowAfterLock.isBefore(lockedQuote.getExpiresAt())) {
            throw new BusinessRuleException("QUOTE_EXPIRED",
                    "Quote " + request.quoteId() + " expired while waiting for lock");
        }

        // 9. Lock inventory nights
        LocalDate lastNight = lockedQuote.getCheckOut().minusDays(1);
        List<DailyInventory> lockedInventory = inventoryRepository.lockNightsForHold(
                lockedQuote.getHotelId(),
                lockedQuote.getRoomTypeId(),
                lockedQuote.getCheckIn(),
                lastNight);

        // 10. Verify all expected nights are present in locked inventory
        int expectedNights = (int) ChronoUnit.DAYS.between(lockedQuote.getCheckIn(), lockedQuote.getCheckOut());
        if (lockedInventory.size() != expectedNights) {
            throw new BusinessRuleException("INVENTORY_UNAVAILABLE",
                    "Expected " + expectedNights + " night(s) but locked " + lockedInventory.size());
        }

        // 11. Verify each night has sufficient inventory
        int rooms = lockedQuote.getRooms();
        for (DailyInventory inv : lockedInventory) {
            if (inv.isStopSell() || inv.getAvailable() < rooms) {
                throw new BusinessRuleException("INVENTORY_UNAVAILABLE",
                        "Insufficient inventory for date " + inv.getStayDate());
            }
        }

        // 12. Calculate hold expiry from post-lock time, capped at quote expiry
        Instant holdExpiry = nowAfterLock.plusSeconds(holdTtlMinutes * 60L);
        if (holdExpiry.isAfter(lockedQuote.getExpiresAt())) {
            holdExpiry = lockedQuote.getExpiresAt();
        }

        // 13. Create hold entity
        HotelHold hold = new HotelHold();
        hold.setQuoteId(request.quoteId());
        hold.setHotelId(lockedQuote.getHotelId());
        hold.setRoomTypeId(lockedQuote.getRoomTypeId());
        hold.setRatePlanId(lockedQuote.getRatePlanId());
        hold.setCheckIn(lockedQuote.getCheckIn());
        hold.setCheckOut(lockedQuote.getCheckOut());
        hold.setRooms(rooms);
        hold.setStatus(HoldStatus.HELD);
        hold.setIdempotencyKey(request.idempotencyKey());
        hold.setPrincipalUserId(request.principalUserId());
        hold.setOrderCorrelationId(request.orderCorrelationId());
        hold.setCurrency(lockedQuote.getCurrency());
        hold.setTotalAmount(lockedQuote.getTotalAmount());
        hold.setExpiresAt(holdExpiry);

        // 14. Save hold with flush to enforce idempotency constraint
        HotelHold saved;
        try {
            saved = holdRepository.saveAndFlush(hold);
        } catch (DataIntegrityViolationException e) {
            log.warn("Hold insert rejected by constraint: {}", e.getMostSpecificCause().getMessage());
            String cause = String.valueOf(e.getMostSpecificCause().getMessage());
            if (!cause.contains("uq_hotel_holds_idempotency")) {
                throw e;
            }
            throw new BusinessRuleException("IDEMPOTENCY_CONFLICT",
                    "Idempotency constraint violated for key " + request.idempotencyKey());
        }

        // 15. Mutate inventory AFTER successful flush: increment held counter by rooms for each night
        for (DailyInventory inv : lockedInventory) {
            inv.setHeld(inv.getHeld() + rooms);
        }
        inventoryRepository.saveAll(lockedInventory);

        log.info("Hold created: id={}, quoteId={}, idempotencyKey={}", saved.getId(), saved.getQuoteId(),
                saved.getIdempotencyKey());
        return toResponse(saved);
    }

    @Override
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public HotelHoldResponse confirm(UUID holdId) {
        // Repeat-idempotent: if already CONFIRMED, return existing
        Optional<HotelHold> maybeHold = holdRepository.findById(holdId);
        if (maybeHold.isPresent() && maybeHold.get().getStatus() == HoldStatus.CONFIRMED) {
            return toResponse(maybeHold.get());
        }

        HotelHold hold = holdRepository.findByIdForUpdate(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("Hold", "id", holdId));

        // Repeat-confirm idempotency: if already CONFIRMED (lock race), return existing
        if (hold.getStatus() == HoldStatus.CONFIRMED) {
            return toResponse(hold);
        }

        // Status must be HELD before any processing
        if (hold.getStatus() != HoldStatus.HELD) {
            throw new BusinessRuleException("HOLD_INVALID_STATE",
                    "Hold " + holdId + " is not in HELD state, current status: " + hold.getStatus());
        }

        // Check if hold has expired while waiting for lock
        if (!Instant.now(clock).isBefore(hold.getExpiresAt())) {
            // Expire the hold: decrement held on inventory, set status EXPIRED
            LocalDate lastNight = hold.getCheckOut().minusDays(1);
            List<DailyInventory> inventory = inventoryRepository.lockNightsForHold(
                    hold.getHotelId(),
                    hold.getRoomTypeId(),
                    hold.getCheckIn(),
                    lastNight);

            int rooms = hold.getRooms();
            for (DailyInventory inv : inventory) {
                if (inv.getHeld() < rooms) {
                    throw new IllegalStateException("Held count " + inv.getHeld() + " < rooms " + rooms + " for date " + inv.getStayDate());
                }
                inv.setHeld(inv.getHeld() - rooms);
            }
            inventoryRepository.saveAll(inventory);

            hold.setStatus(HoldStatus.EXPIRED);
            hold.setReleasedAt(Instant.now(clock));
            holdRepository.save(hold);

            throw new BusinessRuleException("HOLD_EXPIRED",
                    "Hold " + holdId + " has expired");
        }

        // Mutate inventory: move from held to confirmed
        LocalDate lastNight = hold.getCheckOut().minusDays(1);
        List<DailyInventory> inventory = inventoryRepository.lockNightsForHold(
                hold.getHotelId(),
                hold.getRoomTypeId(),
                hold.getCheckIn(),
                lastNight);

        int rooms = hold.getRooms();
        for (DailyInventory inv : inventory) {
            if (inv.getHeld() < rooms) {
                throw new IllegalStateException("Held count " + inv.getHeld() + " < rooms " + rooms + " for date " + inv.getStayDate());
            }
            inv.setHeld(inv.getHeld() - rooms);
            inv.setConfirmed(inv.getConfirmed() + rooms);
        }
        inventoryRepository.saveAll(inventory);

        // Transition hold
        hold.setStatus(HoldStatus.CONFIRMED);
        hold.setConfirmedAt(Instant.now(clock));
        HotelHold saved = holdRepository.save(hold);

        log.info("Hold confirmed: id={}", saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public HotelHoldResponse release(UUID holdId) {
        // Repeat-idempotent: if already RELEASED or EXPIRED, return existing
        Optional<HotelHold> maybeHold = holdRepository.findById(holdId);
        if (maybeHold.isPresent()) {
            HoldStatus status = maybeHold.get().getStatus();
            if (status == HoldStatus.RELEASED || status == HoldStatus.EXPIRED) {
                return toResponse(maybeHold.get());
            }
        }

        HotelHold hold = holdRepository.findByIdForUpdate(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("Hold", "id", holdId));

        if (hold.getStatus() != HoldStatus.HELD) {
            throw new BusinessRuleException("HOLD_INVALID_STATE",
                    "Hold " + holdId + " is not in HELD state, current status: " + hold.getStatus());
        }

        // Mutate inventory: decrement held counter
        LocalDate lastNight = hold.getCheckOut().minusDays(1);
        List<DailyInventory> inventory = inventoryRepository.lockNightsForHold(
                hold.getHotelId(),
                hold.getRoomTypeId(),
                hold.getCheckIn(),
                lastNight);

        int rooms = hold.getRooms();
        for (DailyInventory inv : inventory) {
            if (inv.getHeld() < rooms) {
                throw new IllegalStateException("Held count " + inv.getHeld() + " < rooms " + rooms + " for date " + inv.getStayDate());
            }
            inv.setHeld(inv.getHeld() - rooms);
        }
        inventoryRepository.saveAll(inventory);

        // Transition hold
        hold.setStatus(HoldStatus.RELEASED);
        hold.setReleasedAt(Instant.now(clock));
        HotelHold saved = holdRepository.save(hold);

        log.info("Hold released: id={}", saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public HotelHoldResponse findById(UUID holdId) {
        HotelHold hold = holdRepository.findById(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("Hold", "id", holdId));
        return toResponse(hold);
    }

    /**
     * Expiry scheduler method - invoked via proxy to ensure transactional behavior.
     */
    @Override
    @Transactional
    public int releaseExpired(int batchSize) {
        Instant now = Instant.now(clock);
        PageRequest pageRequest = PageRequest.of(0, batchSize);
        // Use <= now for inclusive expiry: exact equality means expired
        List<HotelHold> expired = holdRepository.findExpiredHeld(now, pageRequest);

        int count = 0;
        for (HotelHold hold : expired) {
            // Lock the hold to prevent concurrent processing
            Optional<HotelHold> locked = holdRepository.findByIdForUpdate(hold.getId());
            if (locked.isPresent()) {
                HotelHold h = locked.get();
                if (h.getStatus() == HoldStatus.HELD) {
                    // Double-check expiry (inclusive: <= now)
                    Instant nowAfterLock = Instant.now(clock);
                    if (!nowAfterLock.isBefore(h.getExpiresAt())) {
                        // Mutate inventory: decrement held counter
                        LocalDate lastNight = h.getCheckOut().minusDays(1);
                        List<DailyInventory> inventory = inventoryRepository.lockNightsForHold(
                                h.getHotelId(),
                                h.getRoomTypeId(),
                                h.getCheckIn(),
                                lastNight);

                        int rooms = h.getRooms();
                        for (DailyInventory inv : inventory) {
                            if (inv.getHeld() < rooms) {
                                throw new IllegalStateException("Held count " + inv.getHeld() + " < rooms " + rooms + " for date " + inv.getStayDate());
                            }
                            inv.setHeld(inv.getHeld() - rooms);
                        }
                        inventoryRepository.saveAll(inventory);

                        h.setStatus(HoldStatus.EXPIRED);
                        h.setReleasedAt(now);
                        holdRepository.save(h);
                        count++;
                        log.info("Hold expired: id={}, expiresAt={}", h.getId(), h.getExpiresAt());
                    }
                }
            }
        }
        return count;
    }

    private HotelHoldResponse toResponse(HotelHold hold) {
        return new HotelHoldResponse(
                hold.getId(),
                hold.getQuoteId(),
                hold.getHotelId(),
                hotelRepository.findById(hold.getHotelId()).map(h -> h.getOrganizationId()).orElse(null),
                hold.getRoomTypeId(),
                hold.getRatePlanId(),
                hold.getCheckIn(),
                hold.getCheckOut(),
                hold.getNightCount(),
                hold.getRooms(),
                hold.getStatus(),
                hold.getCurrency(),
                hold.getTotalAmount(),
                hold.getExpiresAt(),
                hold.getConfirmedAt(),
                hold.getReleasedAt(),
                hold.getCreatedAt()
        );
    }
}
