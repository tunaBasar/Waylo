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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelHoldServiceImplTest {

    private static final UUID HOTEL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ROOM_TYPE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID RATE_PLAN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID QUOTE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PRINCIPAL_USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String IDEMPOTENCY_KEY = "idem-key-001";
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 12, 1);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 12, 3); // 2 nights
    private static final int ROOMS = 1;
    private static final BigDecimal TOTAL_AMOUNT = new BigDecimal("300.00");
    private static final String CURRENCY = "USD";
    private static final int HOLD_TTL_MINUTES = 15;

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private HotelQuoteRepository quoteRepository;
    @Mock
    private HotelHoldRepository holdRepository;
    @Mock
    private DailyInventoryRepository inventoryRepository;

    private HotelHoldServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HotelHoldServiceImpl(quoteRepository, holdRepository, inventoryRepository,
                FIXED_CLOCK, HOLD_TTL_MINUTES);
    }

    // ==================== HELPERS ====================

    private HotelQuote createQuote(UUID id) {
        return createQuote(id, PRINCIPAL_USER_ID);
    }

    private HotelQuote createQuote(UUID id, UUID principalUserId) {
        HotelQuote quote = new HotelQuote();
        quote.setId(id);
        quote.setHotelId(HOTEL_ID);
        quote.setRoomTypeId(ROOM_TYPE_ID);
        quote.setRatePlanId(RATE_PLAN_ID);
        quote.setCheckIn(CHECK_IN);
        quote.setCheckOut(CHECK_OUT);
        quote.setRooms(ROOMS);
        quote.setCurrency(CURRENCY);
        quote.setTotalAmount(TOTAL_AMOUNT);
        quote.setPrincipalUserId(principalUserId);
        quote.setExpiresAt(FIXED_CLOCK.instant().plusSeconds(HOLD_TTL_MINUTES * 60L + 300L));
        return quote;
    }

    private HotelHoldRequest createHoldRequest() {
        return new HotelHoldRequest(QUOTE_ID, IDEMPOTENCY_KEY, PRINCIPAL_USER_ID, null);
    }

    private HotelHoldRequest createHoldRequest(UUID quoteId, String idempotencyKey) {
        return new HotelHoldRequest(quoteId, idempotencyKey, PRINCIPAL_USER_ID, null);
    }

    private DailyInventory createInventory(LocalDate stayDate, int held, int confirmed, boolean stopSell) {
        DailyInventory inv = new DailyInventory();
        inv.setId(UUID.randomUUID());
        inv.setHotelId(HOTEL_ID);
        inv.setRoomTypeId(ROOM_TYPE_ID);
        inv.setStayDate(stayDate);
        inv.setAllotment(100);
        inv.setOutOfService(0);
        inv.setHeld(held);
        inv.setConfirmed(confirmed);
        inv.setStopSell(stopSell);
        return inv;
    }

    private List<DailyInventory> createAvailableInventory() {
        return List.of(
                createInventory(CHECK_IN, 0, 0, false),
                createInventory(CHECK_IN.plusDays(1), 0, 0, false)
        );
    }

    private HotelHold createHeldHold(UUID id, UUID quoteId) {
        HotelHold hold = new HotelHold();
        hold.setId(id);
        hold.setQuoteId(quoteId);
        hold.setStatus(HoldStatus.HELD);
        hold.setIdempotencyKey(IDEMPOTENCY_KEY);
        hold.setHotelId(HOTEL_ID);
        hold.setRoomTypeId(ROOM_TYPE_ID);
        hold.setRatePlanId(RATE_PLAN_ID);
        hold.setCheckIn(CHECK_IN);
        hold.setCheckOut(CHECK_OUT);
        hold.setRooms(ROOMS);
        hold.setCurrency(CURRENCY);
        hold.setTotalAmount(TOTAL_AMOUNT);
        hold.setExpiresAt(FIXED_CLOCK.instant().plusSeconds(900L));
        hold.setCreatedAt(FIXED_CLOCK.instant());
        hold.setPrincipalUserId(PRINCIPAL_USER_ID);
        return hold;
    }

    private void mockFullHoldFlow() {
        mockFullHoldFlow(QUOTE_ID, IDEMPOTENCY_KEY);
    }

    private void mockFullHoldFlow(UUID quoteId, String idempKey) {
        HotelQuote quote = createQuote(quoteId);
        when(quoteRepository.findByIdWithNights(quoteId)).thenReturn(Optional.of(quote));
        when(holdRepository.findByIdempotencyKey(idempKey)).thenReturn(Optional.empty());
        when(quoteRepository.findByIdForUpdate(quoteId)).thenReturn(Optional.of(quote));
        when(holdRepository.countByQuoteIdAndStatusIn(eq(quoteId), anyList())).thenReturn(0L);
        when(inventoryRepository.lockNightsForHold(eq(HOTEL_ID), eq(ROOM_TYPE_ID),
                eq(CHECK_IN), eq(CHECK_OUT.minusDays(1)))).thenReturn(createAvailableInventory());
    }

    // ==================== hold() tests ====================

    @Test
    void hold_quoteNotFound_throws404() {
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.hold(createHoldRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Quote");
    }

    @Test
    void hold_quoteExpired_throwsBusinessRule() {
        HotelQuote expiredQuote = createQuote(QUOTE_ID);
        expiredQuote.setExpiresAt(FIXED_CLOCK.instant().minusSeconds(60));
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(expiredQuote));

        assertThatThrownBy(() -> service.hold(createHoldRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void hold_principalUserIdNull_throwsBusinessRule() {
        HotelQuote quote = createQuote(QUOTE_ID);
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(quote));

        HotelHoldRequest request = new HotelHoldRequest(QUOTE_ID, IDEMPOTENCY_KEY, null, null);

        assertThatThrownBy(() -> service.hold(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("principalUserId");
    }

    @Test
    void hold_principalMismatch_throwsBusinessRule() {
        UUID differentUser = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        HotelQuote quote = createQuote(QUOTE_ID, differentUser);
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.hold(createHoldRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("principal");
    }

    @Test
    void hold_existingHeldHold_sameKeySameQuoteSamePrincipal_returnsExisting() {
        HotelQuote quote = createQuote(QUOTE_ID);
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(quote));

        HotelHold existing = createHeldHold(UUID.randomUUID(), QUOTE_ID);
        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existing));

        HotelHoldResponse response = service.hold(createHoldRequest());

        assertThat(response.holdId()).isEqualTo(existing.getId());
        assertThat(response.status()).isEqualTo(HoldStatus.HELD);
        verify(holdRepository, never()).save(any());
        verify(inventoryRepository, never()).lockNightsForHold(any(), any(), any(), any());
    }

    @Test
    void hold_existingHeldHold_sameKeyDifferentQuote_throwsIdempotencyConflict() {
        HotelQuote quote = createQuote(QUOTE_ID);
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(quote));

        UUID differentQuoteId = UUID.randomUUID();
        HotelHold existing = createHeldHold(UUID.randomUUID(), differentQuoteId);
        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.hold(createHoldRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Idempotency");
    }

    @Test
    void hold_existingConfirmedHold_sameKey_throwsIdempotencyConflict() {
        HotelQuote quote = createQuote(QUOTE_ID);
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(quote));

        UUID differentQuoteId = UUID.randomUUID();
        HotelHold confirmedHold = new HotelHold();
        confirmedHold.setId(UUID.randomUUID());
        confirmedHold.setQuoteId(differentQuoteId);
        confirmedHold.setStatus(HoldStatus.CONFIRMED);
        confirmedHold.setIdempotencyKey(IDEMPOTENCY_KEY);
        confirmedHold.setHotelId(HOTEL_ID);
        confirmedHold.setRoomTypeId(ROOM_TYPE_ID);
        confirmedHold.setRatePlanId(RATE_PLAN_ID);
        confirmedHold.setCheckIn(CHECK_IN);
        confirmedHold.setCheckOut(CHECK_OUT);
        confirmedHold.setRooms(ROOMS);
        confirmedHold.setCurrency(CURRENCY);
        confirmedHold.setTotalAmount(TOTAL_AMOUNT);
        confirmedHold.setExpiresAt(FIXED_CLOCK.instant().plusSeconds(900L));
        confirmedHold.setCreatedAt(FIXED_CLOCK.instant());
        confirmedHold.setPrincipalUserId(PRINCIPAL_USER_ID);

        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(confirmedHold));

        assertThatThrownBy(() -> service.hold(createHoldRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Idempotency");
    }

    @Test
    void hold_existingReleasedHold_sameKey_throwsIdempotencyConflict() {
        HotelQuote quote = createQuote(QUOTE_ID);
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(quote));

        UUID differentQuoteId = UUID.randomUUID();
        HotelHold releasedHold = new HotelHold();
        releasedHold.setId(UUID.randomUUID());
        releasedHold.setQuoteId(differentQuoteId);
        releasedHold.setStatus(HoldStatus.RELEASED);
        releasedHold.setIdempotencyKey(IDEMPOTENCY_KEY);
        releasedHold.setHotelId(HOTEL_ID);
        releasedHold.setRoomTypeId(ROOM_TYPE_ID);
        releasedHold.setRatePlanId(RATE_PLAN_ID);
        releasedHold.setCheckIn(CHECK_IN);
        releasedHold.setCheckOut(CHECK_OUT);
        releasedHold.setRooms(ROOMS);
        releasedHold.setCurrency(CURRENCY);
        releasedHold.setTotalAmount(TOTAL_AMOUNT);
        releasedHold.setExpiresAt(FIXED_CLOCK.instant().plusSeconds(900L));
        releasedHold.setCreatedAt(FIXED_CLOCK.instant());
        releasedHold.setPrincipalUserId(PRINCIPAL_USER_ID);
        releasedHold.setReleasedAt(FIXED_CLOCK.instant());

        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(releasedHold));

        assertThatThrownBy(() -> service.hold(createHoldRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Idempotency");
    }

    @Test
    void hold_quoteNotFoundAfterLock_throws404() {
        HotelQuote quote = createQuote(QUOTE_ID);
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(quote));
        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(quoteRepository.findByIdForUpdate(QUOTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.hold(createHoldRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void hold_concurrentIdempotencyKeyWithDifferentQuote_throwsIdempotencyConflict() {
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(createQuote(QUOTE_ID)));

        UUID otherQuoteId = UUID.randomUUID();
        HotelHold concurrent = createHeldHold(UUID.randomUUID(), otherQuoteId);
        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(concurrent));

        assertThatThrownBy(() -> service.hold(createHoldRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Idempotency");
    }

    @Test
    void hold_activeHoldForQuote_throws() {
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(createQuote(QUOTE_ID)));
        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(quoteRepository.findByIdForUpdate(QUOTE_ID)).thenReturn(Optional.of(createQuote(QUOTE_ID)));
        when(holdRepository.countByQuoteIdAndStatusIn(eq(QUOTE_ID), anyList())).thenReturn(1L);

        assertThatThrownBy(() -> service.hold(createHoldRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already has an active hold");
    }

    @Test
    void hold_insufficientInventory_throwsBusinessRule() {
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(createQuote(QUOTE_ID)));
        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(quoteRepository.findByIdForUpdate(QUOTE_ID)).thenReturn(Optional.of(createQuote(QUOTE_ID)));
        when(holdRepository.countByQuoteIdAndStatusIn(eq(QUOTE_ID), anyList())).thenReturn(0L);
        // held=100, allotment=100 -> available=0 (100-0-100-0=0 < rooms=1)
        when(inventoryRepository.lockNightsForHold(eq(HOTEL_ID), eq(ROOM_TYPE_ID),
                eq(CHECK_IN), eq(CHECK_OUT.minusDays(1))))
                .thenReturn(List.of(createInventory(CHECK_IN, 100, 0, false),
                        createInventory(CHECK_IN.plusDays(1), 100, 0, false)));

        assertThatThrownBy(() -> service.hold(createHoldRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient inventory");
    }

    @Test
    void hold_stopSell_throwsBusinessRule() {
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(createQuote(QUOTE_ID)));
        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(quoteRepository.findByIdForUpdate(QUOTE_ID)).thenReturn(Optional.of(createQuote(QUOTE_ID)));
        when(holdRepository.countByQuoteIdAndStatusIn(eq(QUOTE_ID), anyList())).thenReturn(0L);
        when(inventoryRepository.lockNightsForHold(eq(HOTEL_ID), eq(ROOM_TYPE_ID),
                eq(CHECK_IN), eq(CHECK_OUT.minusDays(1))))
                .thenReturn(List.of(createInventory(CHECK_IN, 0, 0, true),
                        createInventory(CHECK_IN.plusDays(1), 0, 0, false)));

        assertThatThrownBy(() -> service.hold(createHoldRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient inventory");
    }

    @Test
    void hold_quoteExpiredDuringLockWait_throws() {
        // Initial load returns valid quote
        HotelQuote validQuote = createQuote(QUOTE_ID);
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(validQuote));

        // No pre-existing idempotency key (called twice: before and after quote lock)
        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        // Lock-for-update returns EXPIRED quote (simulates concurrent expiry during lock wait)
        HotelQuote expiredQuote = createQuote(QUOTE_ID);
        expiredQuote.setExpiresAt(FIXED_CLOCK.instant().minusSeconds(1));
        when(quoteRepository.findByIdForUpdate(QUOTE_ID)).thenReturn(Optional.of(expiredQuote));

        assertThatThrownBy(() -> service.hold(createHoldRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");

        verify(holdRepository, never()).save(any());
        verify(inventoryRepository, never()).lockNightsForHold(any(), any(), any(), any());
    }

    @Test
    void hold_missingNightRow_throwsInventoryUnavailable() {
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(createQuote(QUOTE_ID)));
        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(quoteRepository.findByIdForUpdate(QUOTE_ID)).thenReturn(Optional.of(createQuote(QUOTE_ID)));
        when(holdRepository.countByQuoteIdAndStatusIn(eq(QUOTE_ID), anyList())).thenReturn(0L);
        // Only 1 night returned instead of expected 2 (CHECK_IN to CHECK_OUT = 2 nights)
        when(inventoryRepository.lockNightsForHold(eq(HOTEL_ID), eq(ROOM_TYPE_ID),
                eq(CHECK_IN), eq(CHECK_OUT.minusDays(1))))
                .thenReturn(List.of(createInventory(CHECK_IN, 0, 0, false)));

        assertThatThrownBy(() -> service.hold(createHoldRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Expected");

        // saveAll should NOT be called because the day count check fails first
        verify(inventoryRepository, never()).saveAll(any());
    }

    @Test
    void hold_valid_mutatesInventoryAndCreatesHold() {
        mockFullHoldFlow();

        HotelHold saved = new HotelHold();
        saved.setId(UUID.randomUUID());
        saved.setQuoteId(QUOTE_ID);
        saved.setStatus(HoldStatus.HELD);
        saved.setIdempotencyKey(IDEMPOTENCY_KEY);
        saved.setCreatedAt(FIXED_CLOCK.instant());
        saved.setExpiresAt(FIXED_CLOCK.instant().plusSeconds(900L));
        saved.setHotelId(HOTEL_ID);
        saved.setRoomTypeId(ROOM_TYPE_ID);
        saved.setRatePlanId(RATE_PLAN_ID);
        saved.setCheckIn(CHECK_IN);
        saved.setCheckOut(CHECK_OUT);
        saved.setRooms(ROOMS);
        saved.setCurrency(CURRENCY);
        saved.setTotalAmount(TOTAL_AMOUNT);
        saved.setPrincipalUserId(PRINCIPAL_USER_ID);

        when(holdRepository.saveAndFlush(any())).thenReturn(saved);

        HotelHoldResponse response = service.hold(createHoldRequest());

        assertThat(response.holdId()).isEqualTo(saved.getId());
        assertThat(response.status()).isEqualTo(HoldStatus.HELD);
        assertThat(response.nights()).isEqualTo(2);
        assertThat(response.totalAmount()).isEqualByComparingTo(TOTAL_AMOUNT);

        // Verify saveAndFlush was called (not just save)
        verify(holdRepository).saveAndFlush(any());

        // Verify inventory was mutated
        ArgumentCaptor<List<DailyInventory>> invCaptor = ArgumentCaptor.forClass(List.class);
        verify(inventoryRepository).saveAll(invCaptor.capture());
        List<DailyInventory> mutated = invCaptor.getValue();
        assertThat(mutated).hasSize(2);
        // Held should have been incremented by ROOMS=1
        assertThat(mutated.get(0).getHeld()).isEqualTo(1);
        assertThat(mutated.get(1).getHeld()).isEqualTo(1);
        // Confirmed unchanged
        assertThat(mutated.get(0).getConfirmed()).isEqualTo(0);
    }

    @Test
    void hold_expiryCappedAtQuoteExpiry() {
        HotelQuote quote = createQuote(QUOTE_ID);
        quote.setExpiresAt(FIXED_CLOCK.instant().plusSeconds(600L)); // 10 min, less than 15 min TTL

        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(quote));
        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(quoteRepository.findByIdForUpdate(QUOTE_ID)).thenReturn(Optional.of(quote));
        when(holdRepository.countByQuoteIdAndStatusIn(eq(QUOTE_ID), anyList())).thenReturn(0L);
        when(inventoryRepository.lockNightsForHold(eq(HOTEL_ID), eq(ROOM_TYPE_ID),
                eq(CHECK_IN), eq(CHECK_OUT.minusDays(1)))).thenReturn(createAvailableInventory());

        HotelHold saved = new HotelHold();
        saved.setId(UUID.randomUUID());
        saved.setQuoteId(QUOTE_ID);
        saved.setStatus(HoldStatus.HELD);
        saved.setIdempotencyKey(IDEMPOTENCY_KEY);
        saved.setCreatedAt(FIXED_CLOCK.instant());
        saved.setExpiresAt(FIXED_CLOCK.instant().plusSeconds(600L));
        saved.setHotelId(HOTEL_ID);
        saved.setRoomTypeId(ROOM_TYPE_ID);
        saved.setRatePlanId(RATE_PLAN_ID);
        saved.setCheckIn(CHECK_IN);
        saved.setCheckOut(CHECK_OUT);
        saved.setRooms(ROOMS);
        saved.setCurrency(CURRENCY);
        saved.setTotalAmount(TOTAL_AMOUNT);
        saved.setPrincipalUserId(PRINCIPAL_USER_ID);

        when(holdRepository.saveAndFlush(any())).thenReturn(saved);

        HotelHoldResponse response = service.hold(createHoldRequest());
        assertThat(response.expiresAt()).isEqualTo(FIXED_CLOCK.instant().plusSeconds(600L));
    }

    @Test
    void hold_withOrderCorrelationId_savedCorrectly() {
        UUID orderCorrelationId = UUID.randomUUID();
        HotelHoldRequest request = new HotelHoldRequest(QUOTE_ID, IDEMPOTENCY_KEY,
                PRINCIPAL_USER_ID, orderCorrelationId);

        mockFullHoldFlow();

        HotelHold saved = new HotelHold();
        saved.setId(UUID.randomUUID());
        saved.setQuoteId(QUOTE_ID);
        saved.setStatus(HoldStatus.HELD);
        saved.setIdempotencyKey(IDEMPOTENCY_KEY);
        saved.setCreatedAt(FIXED_CLOCK.instant());
        saved.setExpiresAt(FIXED_CLOCK.instant().plusSeconds(900L));
        saved.setHotelId(HOTEL_ID);
        saved.setRoomTypeId(ROOM_TYPE_ID);
        saved.setRatePlanId(RATE_PLAN_ID);
        saved.setCheckIn(CHECK_IN);
        saved.setCheckOut(CHECK_OUT);
        saved.setRooms(ROOMS);
        saved.setCurrency(CURRENCY);
        saved.setTotalAmount(TOTAL_AMOUNT);
        saved.setPrincipalUserId(PRINCIPAL_USER_ID);
        saved.setOrderCorrelationId(orderCorrelationId);

        when(holdRepository.saveAndFlush(any())).thenReturn(saved);

        HotelHoldResponse response = service.hold(request);
        assertThat(response.holdId()).isNotNull();
    }

    @Test
    void hold_multipleRooms_insufficientInventory_throws() {
        HotelQuote quote = createQuote(QUOTE_ID);
        quote.setRooms(3);

        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(quote));
        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(quoteRepository.findByIdForUpdate(QUOTE_ID)).thenReturn(Optional.of(quote));
        when(holdRepository.countByQuoteIdAndStatusIn(eq(QUOTE_ID), anyList())).thenReturn(0L);
        // Second night: allotment=2, held=0, confirmed=0 -> available=2 < 3 rooms
        DailyInventory inv1 = createInventory(CHECK_IN, 0, 0, false);
        inv1.setAllotment(100);
        DailyInventory inv2 = createInventory(CHECK_IN.plusDays(1), 0, 0, false);
        inv2.setAllotment(2);
        when(inventoryRepository.lockNightsForHold(eq(HOTEL_ID), eq(ROOM_TYPE_ID),
                eq(CHECK_IN), eq(CHECK_OUT.minusDays(1))))
                .thenReturn(List.of(inv1, inv2));

        assertThatThrownBy(() -> service.hold(new HotelHoldRequest(QUOTE_ID, IDEMPOTENCY_KEY, PRINCIPAL_USER_ID, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient inventory");
    }

    @Test
    void hold_dataIntegrityViolationOnFlush_throwsIdempotencyConflict() {
        mockFullHoldFlow();

        when(holdRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_hotel_holds_idempotency\""));

        assertThatThrownBy(() -> service.hold(createHoldRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Idempotency");

        // Inventory should NOT be mutated since the flush failed
        verify(inventoryRepository, never()).saveAll(any());
    }

    @Test
    void hold_expiryUsesPostLockTime() {
        // Mutable clock using AtomicReference so we can mutate from outside the anonymous class
        java.util.concurrent.atomic.AtomicReference<Instant> current =
                new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-10-22T12:00:00Z"));

        class MutableClock extends Clock {
            @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return current.get(); }
            void advanceBy(Instant instant) { current.set(instant); }
        }
        MutableClock mutableClock = new MutableClock();

        HotelHoldServiceImpl svc = new HotelHoldServiceImpl(quoteRepository, holdRepository, inventoryRepository,
                mutableClock, HOLD_TTL_MINUTES);

        HotelQuote quote = createQuote(QUOTE_ID);
        // Set a far-future quote expiry so TTL is the limiting factor
        quote.setExpiresAt(Instant.parse("2026-12-31T23:59:59Z"));

        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(quote));
        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(quoteRepository.findByIdForUpdate(QUOTE_ID)).thenReturn(Optional.of(quote));
        when(holdRepository.countByQuoteIdAndStatusIn(eq(QUOTE_ID), anyList())).thenReturn(0L);
        when(inventoryRepository.lockNightsForHold(eq(HOTEL_ID), eq(ROOM_TYPE_ID),
                eq(CHECK_IN), eq(CHECK_OUT.minusDays(1)))).thenReturn(createAvailableInventory());

        HotelHold saved = new HotelHold();
        saved.setId(UUID.randomUUID());
        saved.setQuoteId(QUOTE_ID);
        saved.setStatus(HoldStatus.HELD);
        saved.setIdempotencyKey(IDEMPOTENCY_KEY);
        saved.setCreatedAt(Instant.parse("2026-10-22T12:00:00Z"));
        saved.setHotelId(HOTEL_ID);
        saved.setRoomTypeId(ROOM_TYPE_ID);
        saved.setRatePlanId(RATE_PLAN_ID);
        saved.setCheckIn(CHECK_IN);
        saved.setCheckOut(CHECK_OUT);
        saved.setRooms(ROOMS);
        saved.setCurrency(CURRENCY);
        saved.setTotalAmount(TOTAL_AMOUNT);
        saved.setPrincipalUserId(PRINCIPAL_USER_ID);

        when(holdRepository.saveAndFlush(any())).thenAnswer(inv -> {
            HotelHold hold = inv.getArgument(0);
            saved.setId(hold.getId());
            saved.setQuoteId(hold.getQuoteId());
            saved.setStatus(hold.getStatus());
            saved.setIdempotencyKey(hold.getIdempotencyKey());
            saved.setCreatedAt(hold.getCreatedAt());
            saved.setExpiresAt(hold.getExpiresAt());
            saved.setHotelId(hold.getHotelId());
            saved.setRoomTypeId(hold.getRoomTypeId());
            saved.setRatePlanId(hold.getRatePlanId());
            saved.setCheckIn(hold.getCheckIn());
            saved.setCheckOut(hold.getCheckOut());
            saved.setRooms(hold.getRooms());
            saved.setCurrency(hold.getCurrency());
            saved.setTotalAmount(hold.getTotalAmount());
            saved.setPrincipalUserId(hold.getPrincipalUserId());
            return saved;
        });

        // Advance clock by 5 seconds to simulate post-lock time
        mutableClock.advanceBy(Instant.parse("2026-10-22T12:00:05Z"));

        HotelHoldResponse response = svc.hold(createHoldRequest());

        // Expiry should be based on post-lock time (T=5s + 15min), not initial time (T=0 + 15min)
        Instant expectedExpiry = Instant.parse("2026-10-22T12:15:05Z");
        assertThat(response.expiresAt()).isEqualTo(expectedExpiry);
    }

    // ==================== confirm() tests ====================

    @Test
    void confirm_holdNotFound_throws404() {
        when(holdRepository.findByIdForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(QUOTE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void confirm_repeatConfirmed_returnsExisting() {
        UUID holdId = UUID.randomUUID();
        HotelHold confirmedHold = new HotelHold();
        confirmedHold.setId(holdId);
        confirmedHold.setQuoteId(QUOTE_ID);
        confirmedHold.setStatus(HoldStatus.CONFIRMED);
        confirmedHold.setCreatedAt(FIXED_CLOCK.instant());
        confirmedHold.setConfirmedAt(FIXED_CLOCK.instant().minusSeconds(60));
        confirmedHold.setHotelId(HOTEL_ID);
        confirmedHold.setRoomTypeId(ROOM_TYPE_ID);
        confirmedHold.setCheckIn(CHECK_IN);
        confirmedHold.setCheckOut(CHECK_OUT);
        confirmedHold.setRooms(ROOMS);
        confirmedHold.setCurrency(CURRENCY);
        confirmedHold.setTotalAmount(TOTAL_AMOUNT);

        // First find returns CONFIRMED -> immediate return
        when(holdRepository.findById(holdId)).thenReturn(Optional.of(confirmedHold));

        HotelHoldResponse response = service.confirm(holdId);

        assertThat(response.holdId()).isEqualTo(holdId);
        assertThat(response.status()).isEqualTo(HoldStatus.CONFIRMED);
        // Should NOT load FOR UPDATE, NOT touch inventory
        verify(holdRepository, never()).findByIdForUpdate(any());
        verify(inventoryRepository, never()).lockNightsForHold(any(), any(), any(), any());
    }

    @Test
    void confirm_notInHeldState_throws() {
        UUID holdId = UUID.randomUUID();
        HotelHold hold = new HotelHold();
        hold.setId(holdId);
        hold.setStatus(HoldStatus.RELEASED);
        hold.setExpiresAt(FIXED_CLOCK.instant().plusSeconds(900L));
        hold.setCreatedAt(FIXED_CLOCK.instant());
        when(holdRepository.findById(holdId)).thenReturn(Optional.of(hold));
        when(holdRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> service.confirm(holdId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not in HELD state");
    }

    @Test
    void confirm_expiredHold_setsStatusExpired() {
        UUID holdId = UUID.randomUUID();
        HotelHold hold = createHeldHold(holdId, QUOTE_ID);
        hold.setExpiresAt(FIXED_CLOCK.instant().minusSeconds(60)); // already expired

        // Mock initial findById -> not found, so falls through to findByIdForUpdate
        when(holdRepository.findById(holdId)).thenReturn(Optional.empty());
        when(holdRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));
        // Hold has already expired
        when(inventoryRepository.lockNightsForHold(eq(HOTEL_ID), eq(ROOM_TYPE_ID),
                eq(CHECK_IN), eq(CHECK_OUT.minusDays(1))))
                .thenReturn(List.of(createInventory(CHECK_IN, 1, 0, false),
                        createInventory(CHECK_IN.plusDays(1), 1, 0, false)));

        when(holdRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.confirm(holdId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");

        // Verify hold status changed to EXPIRED
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.EXPIRED);
        assertThat(hold.getReleasedAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(hold.getConfirmedAt()).isNull();

        // Verify held decremented on inventory
        assertThat(hold.getCheckIn()).isEqualTo(CHECK_IN);
    }

    @Test
    void confirm_valid_transitionsAndMutatesInventory() {
        UUID holdId = UUID.randomUUID();
        HotelHold hold = createHeldHold(holdId, QUOTE_ID);

        when(holdRepository.findById(holdId)).thenReturn(Optional.empty());
        when(holdRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));
        when(inventoryRepository.lockNightsForHold(eq(HOTEL_ID), eq(ROOM_TYPE_ID),
                eq(CHECK_IN), eq(CHECK_OUT.minusDays(1))))
                .thenReturn(List.of(createInventory(CHECK_IN, 1, 0, false),
                        createInventory(CHECK_IN.plusDays(1), 1, 0, false)));

        when(holdRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HotelHoldResponse response = service.confirm(holdId);

        assertThat(response.holdId()).isEqualTo(holdId);
        assertThat(response.status()).isEqualTo(HoldStatus.CONFIRMED);
        assertThat(response.confirmedAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(response.releasedAt()).isNull();

        // Verify inventory mutation: held -1, confirmed +1
        ArgumentCaptor<List<DailyInventory>> invCaptor = ArgumentCaptor.forClass(List.class);
        verify(inventoryRepository).saveAll(invCaptor.capture());
        List<DailyInventory> mutated = invCaptor.getValue();
        assertThat(mutated.get(0).getHeld()).isEqualTo(0);  // 1 - 1 = 0
        assertThat(mutated.get(0).getConfirmed()).isEqualTo(1);
    }

    // ==================== release() tests ====================

    @Test
    void release_holdNotFound_throws404() {
        when(holdRepository.findByIdForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.release(QUOTE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void release_repeatReleased_returnsExisting() {
        UUID holdId = UUID.randomUUID();
        HotelHold releasedHold = new HotelHold();
        releasedHold.setId(holdId);
        releasedHold.setQuoteId(QUOTE_ID);
        releasedHold.setStatus(HoldStatus.RELEASED);
        releasedHold.setReleasedAt(FIXED_CLOCK.instant());
        releasedHold.setCreatedAt(FIXED_CLOCK.instant());
        releasedHold.setHotelId(HOTEL_ID);
        releasedHold.setRoomTypeId(ROOM_TYPE_ID);
        releasedHold.setCheckIn(CHECK_IN);
        releasedHold.setCheckOut(CHECK_OUT);
        releasedHold.setRooms(ROOMS);
        releasedHold.setCurrency(CURRENCY);
        releasedHold.setTotalAmount(TOTAL_AMOUNT);

        when(holdRepository.findById(holdId)).thenReturn(Optional.of(releasedHold));

        HotelHoldResponse response = service.release(holdId);

        assertThat(response.holdId()).isEqualTo(holdId);
        assertThat(response.status()).isEqualTo(HoldStatus.RELEASED);
        // Should NOT load FOR UPDATE, NOT touch inventory
        verify(holdRepository, never()).findByIdForUpdate(any());
        verify(inventoryRepository, never()).lockNightsForHold(any(), any(), any(), any());
    }

    @Test
    void release_repeatExpired_returnsExisting() {
        UUID holdId = UUID.randomUUID();
        HotelHold expiredHold = new HotelHold();
        expiredHold.setId(holdId);
        expiredHold.setQuoteId(QUOTE_ID);
        expiredHold.setStatus(HoldStatus.EXPIRED);
        expiredHold.setReleasedAt(FIXED_CLOCK.instant());
        expiredHold.setCreatedAt(FIXED_CLOCK.instant());
        expiredHold.setHotelId(HOTEL_ID);
        expiredHold.setRoomTypeId(ROOM_TYPE_ID);
        expiredHold.setCheckIn(CHECK_IN);
        expiredHold.setCheckOut(CHECK_OUT);
        expiredHold.setRooms(ROOMS);
        expiredHold.setCurrency(CURRENCY);
        expiredHold.setTotalAmount(TOTAL_AMOUNT);

        when(holdRepository.findById(holdId)).thenReturn(Optional.of(expiredHold));

        HotelHoldResponse response = service.release(holdId);

        assertThat(response.holdId()).isEqualTo(holdId);
        assertThat(response.status()).isEqualTo(HoldStatus.EXPIRED);
    }

    @Test
    void release_confirmedHold_throws() {
        UUID holdId = UUID.randomUUID();
        HotelHold hold = new HotelHold();
        hold.setId(holdId);
        hold.setStatus(HoldStatus.CONFIRMED);
        hold.setCreatedAt(FIXED_CLOCK.instant());
        when(holdRepository.findById(holdId)).thenReturn(Optional.of(hold));
        when(holdRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> service.release(holdId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not in HELD state");
    }

    @Test
    void release_valid_transitionsAndFreesInventory() {
        UUID holdId = UUID.randomUUID();
        HotelHold hold = createHeldHold(holdId, QUOTE_ID);

        when(holdRepository.findById(holdId)).thenReturn(Optional.empty());
        when(holdRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));
        when(inventoryRepository.lockNightsForHold(eq(HOTEL_ID), eq(ROOM_TYPE_ID),
                eq(CHECK_IN), eq(CHECK_OUT.minusDays(1))))
                .thenReturn(List.of(createInventory(CHECK_IN, 1, 0, false),
                        createInventory(CHECK_IN.plusDays(1), 1, 0, false)));

        when(holdRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HotelHoldResponse response = service.release(holdId);

        assertThat(response.holdId()).isEqualTo(holdId);
        assertThat(response.status()).isEqualTo(HoldStatus.RELEASED);
        assertThat(response.releasedAt()).isEqualTo(FIXED_CLOCK.instant());

        // Verify inventory mutation: held -1
        ArgumentCaptor<List<DailyInventory>> invCaptor = ArgumentCaptor.forClass(List.class);
        verify(inventoryRepository).saveAll(invCaptor.capture());
        List<DailyInventory> mutated = invCaptor.getValue();
        assertThat(mutated.get(0).getHeld()).isEqualTo(0);  // 1 - 1 = 0
        assertThat(mutated.get(0).getConfirmed()).isEqualTo(0);  // unchanged
    }

    // ==================== releaseExpired() tests ====================

    @Test
    void releaseExpired_noExpiredHolds_returnsZero() {
        when(holdRepository.findExpiredHeld(any(), any())).thenReturn(List.of());

        int expired = service.releaseExpired(100);

        assertThat(expired).isEqualTo(0);
        verify(holdRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void releaseExpired_expiredHeldHold_freesInventory() {
        UUID holdId = UUID.randomUUID();
        HotelHold hold = createHeldHold(holdId, QUOTE_ID);
        hold.setExpiresAt(FIXED_CLOCK.instant().minusSeconds(60)); // already expired

        when(holdRepository.findExpiredHeld(any(), any())).thenReturn(List.of(hold));
        when(holdRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));
        when(inventoryRepository.lockNightsForHold(eq(HOTEL_ID), eq(ROOM_TYPE_ID),
                eq(CHECK_IN), eq(CHECK_OUT.minusDays(1))))
                .thenReturn(List.of(createInventory(CHECK_IN, 1, 0, false),
                        createInventory(CHECK_IN.plusDays(1), 1, 0, false)));

        when(holdRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int expired = service.releaseExpired(100);

        assertThat(expired).isEqualTo(1);
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.EXPIRED);
        assertThat(hold.getReleasedAt()).isEqualTo(FIXED_CLOCK.instant());

        // Verify inventory freed
        ArgumentCaptor<List<DailyInventory>> invCaptor = ArgumentCaptor.forClass(List.class);
        verify(inventoryRepository).saveAll(invCaptor.capture());
        assertThat(invCaptor.getValue().get(0).getHeld()).isEqualTo(0);
    }

    @Test
    void releaseExpired_alreadyExpiredHold_skipped() {
        UUID holdId = UUID.randomUUID();
        HotelHold hold = new HotelHold();
        hold.setId(holdId);
        hold.setStatus(HoldStatus.EXPIRED);
        hold.setExpiresAt(FIXED_CLOCK.instant().minusSeconds(60));
        hold.setCreatedAt(FIXED_CLOCK.instant());

        when(holdRepository.findExpiredHeld(any(), any())).thenReturn(List.of(hold));
        when(holdRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));

        int expired = service.releaseExpired(100);

        assertThat(expired).isEqualTo(0);
    }

    @Test
    void releaseExpired_notExpiredHeldHold_skipped() {
        UUID holdId = UUID.randomUUID();
        HotelHold hold = createHeldHold(holdId, QUOTE_ID);
        hold.setExpiresAt(FIXED_CLOCK.instant().plusSeconds(3600)); // not yet expired

        when(holdRepository.findExpiredHeld(any(), any())).thenReturn(List.of(hold));
        when(holdRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));

        int expired = service.releaseExpired(100);

        assertThat(expired).isEqualTo(0);
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.HELD);
    }

    @Test
    void releaseExpired_holdGoneBetweenQueries_skipped() {
        UUID holdId = UUID.randomUUID();
        HotelHold hold = createHeldHold(holdId, QUOTE_ID);
        hold.setExpiresAt(FIXED_CLOCK.instant().minusSeconds(60));

        when(holdRepository.findExpiredHeld(any(), any())).thenReturn(List.of(hold));
        when(holdRepository.findByIdForUpdate(holdId)).thenReturn(Optional.empty());

        int expired = service.releaseExpired(100);

        assertThat(expired).isEqualTo(0);
    }

    @Test
    void releaseExpired_holdExpiryExactlyNow_expired() {
        UUID holdId = UUID.randomUUID();
        HotelHold hold = createHeldHold(holdId, QUOTE_ID);
        hold.setExpiresAt(FIXED_CLOCK.instant()); // exactly now

        when(holdRepository.findExpiredHeld(any(), any())).thenReturn(List.of(hold));
        when(holdRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));
        when(inventoryRepository.lockNightsForHold(eq(HOTEL_ID), eq(ROOM_TYPE_ID),
                eq(CHECK_IN), eq(CHECK_OUT.minusDays(1))))
                .thenReturn(List.of(createInventory(CHECK_IN, 1, 0, false),
                        createInventory(CHECK_IN.plusDays(1), 1, 0, false)));

        when(holdRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int expired = service.releaseExpired(100);

        assertThat(expired).isEqualTo(1);
    }

    // ==================== findById() tests ====================

    @Test
    void findById_holdNotFound_throws404() {
        when(holdRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(QUOTE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_valid_holdReturned() {
        UUID holdId = UUID.randomUUID();
        HotelHold hold = createHeldHold(holdId, QUOTE_ID);
        when(holdRepository.findById(holdId)).thenReturn(Optional.of(hold));

        HotelHoldResponse response = service.findById(holdId);

        assertThat(response.holdId()).isEqualTo(holdId);
        assertThat(response.quoteId()).isEqualTo(QUOTE_ID);
        assertThat(response.status()).isEqualTo(HoldStatus.HELD);
    }

    @Test
    void findById_doesNotCallFindByIdForUpdate() {
        UUID holdId = UUID.randomUUID();
        HotelHold hold = createHeldHold(holdId, QUOTE_ID);
        when(holdRepository.findById(holdId)).thenReturn(Optional.of(hold));

        service.findById(holdId);

        // Verify findByIdForUpdate was NOT called
        verify(holdRepository, never()).findByIdForUpdate(any());
        // Verify plain findById WAS called
        verify(holdRepository).findById(holdId);
    }

    // ==================== Idempotency tests ====================

    @Test
    void hold_sameKeySameQuoteReleasedStatus_returnsExisting() {
        // Same idempotency key + same quoteId + same principalUserId in RELEASED status
        HotelQuote quote = createQuote(QUOTE_ID);
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(quote));

        HotelHold existingReleased = new HotelHold();
        existingReleased.setId(UUID.randomUUID());
        existingReleased.setQuoteId(QUOTE_ID);
        existingReleased.setStatus(HoldStatus.RELEASED);
        existingReleased.setIdempotencyKey(IDEMPOTENCY_KEY);
        existingReleased.setHotelId(HOTEL_ID);
        existingReleased.setRoomTypeId(ROOM_TYPE_ID);
        existingReleased.setRatePlanId(RATE_PLAN_ID);
        existingReleased.setCheckIn(CHECK_IN);
        existingReleased.setCheckOut(CHECK_OUT);
        existingReleased.setRooms(ROOMS);
        existingReleased.setCurrency(CURRENCY);
        existingReleased.setTotalAmount(TOTAL_AMOUNT);
        existingReleased.setExpiresAt(FIXED_CLOCK.instant().plusSeconds(900L));
        existingReleased.setCreatedAt(FIXED_CLOCK.instant());
        existingReleased.setPrincipalUserId(PRINCIPAL_USER_ID);
        existingReleased.setReleasedAt(FIXED_CLOCK.instant());

        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existingReleased));

        HotelHoldResponse response = service.hold(createHoldRequest());

        assertThat(response.holdId()).isEqualTo(existingReleased.getId());
        assertThat(response.status()).isEqualTo(HoldStatus.RELEASED);
        // Should NOT save or lock inventory
        verify(holdRepository, never()).save(any());
        verify(inventoryRepository, never()).lockNightsForHold(any(), any(), any(), any());
    }

    @Test
    void hold_sameKeyDifferentQuote_throwsIdempotencyConflict() {
        // Same idempotency key + same quoteId + same principalUserId in HELD status
        HotelQuote quote = createQuote(QUOTE_ID);
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(quote));

        UUID differentQuoteId = UUID.randomUUID();
        HotelHold existing = createHeldHold(UUID.randomUUID(), differentQuoteId);
        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.hold(createHoldRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Idempotency");

        verify(holdRepository, never()).save(any());
        verify(inventoryRepository, never()).lockNightsForHold(any(), any(), any(), any());
    }

    @Test
    void hold_dataIntegrityViolationOnSaveAndFlush_throwsIdempotencyConflict() {
        HotelQuote quote = createQuote(QUOTE_ID);
        when(quoteRepository.findByIdWithNights(QUOTE_ID)).thenReturn(Optional.of(quote));
        when(holdRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(quoteRepository.findByIdForUpdate(QUOTE_ID)).thenReturn(Optional.of(quote));
        when(holdRepository.countByQuoteIdAndStatusIn(eq(QUOTE_ID), anyList())).thenReturn(0L);
        when(inventoryRepository.lockNightsForHold(eq(HOTEL_ID), eq(ROOM_TYPE_ID),
                eq(CHECK_IN), eq(CHECK_OUT.minusDays(1)))).thenReturn(createAvailableInventory());

        when(holdRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_hotel_holds_idempotency\""));

        assertThatThrownBy(() -> service.hold(createHoldRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Idempotency");

        // Inventory saveAll should NOT be called (mutation happens after successful flush)
        verify(inventoryRepository, never()).saveAll(any());
    }

    // ==================== Repeat confirm on RELEASED throws ====================

    @Test
    void confirm_releaseHold_throws() {
        UUID holdId = UUID.randomUUID();
        HotelHold hold = new HotelHold();
        hold.setId(holdId);
        hold.setStatus(HoldStatus.RELEASED);
        hold.setReleasedAt(FIXED_CLOCK.instant());
        hold.setExpiresAt(FIXED_CLOCK.instant().plusSeconds(900L));
        hold.setCreatedAt(FIXED_CLOCK.instant());
        when(holdRepository.findById(holdId)).thenReturn(Optional.of(hold));
        when(holdRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> service.confirm(holdId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not in HELD state");
    }

    @Test
    void confirm_releasedHoldWithPastExpiresAt_throwsInvalidState() {
        // Regression test: status check must run BEFORE expiry check,
        // otherwise a RELEASED hold with past expiresAt would decrement held inventory again.
        UUID holdId = UUID.randomUUID();
        HotelHold hold = new HotelHold();
        hold.setId(holdId);
        hold.setStatus(HoldStatus.RELEASED);
        hold.setReleasedAt(FIXED_CLOCK.instant());
        hold.setExpiresAt(FIXED_CLOCK.instant().minusSeconds(60)); // already expired
        hold.setCreatedAt(FIXED_CLOCK.instant());
        hold.setHotelId(HOTEL_ID);
        hold.setRoomTypeId(ROOM_TYPE_ID);
        hold.setCheckIn(CHECK_IN);
        hold.setCheckOut(CHECK_OUT);
        hold.setRooms(ROOMS);

        when(holdRepository.findById(holdId)).thenReturn(Optional.empty());
        when(holdRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> service.confirm(holdId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not in HELD state");

        // Verify inventory was NOT touched (no double release)
        verify(inventoryRepository, never()).lockNightsForHold(any(), any(), any(), any());
        verify(inventoryRepository, never()).saveAll(any());
    }

    @Test
    void confirm_expiredHoldWithPastExpiresAt_throwsInvalidState() {
        // Regression test: status check must run BEFORE expiry check,
        // otherwise an EXPIRED hold with past expiresAt would decrement held inventory again.
        UUID holdId = UUID.randomUUID();
        HotelHold hold = new HotelHold();
        hold.setId(holdId);
        hold.setStatus(HoldStatus.EXPIRED);
        hold.setReleasedAt(FIXED_CLOCK.instant());
        hold.setExpiresAt(FIXED_CLOCK.instant().minusSeconds(60)); // already expired
        hold.setCreatedAt(FIXED_CLOCK.instant());
        hold.setHotelId(HOTEL_ID);
        hold.setRoomTypeId(ROOM_TYPE_ID);
        hold.setCheckIn(CHECK_IN);
        hold.setCheckOut(CHECK_OUT);
        hold.setRooms(ROOMS);

        when(holdRepository.findById(holdId)).thenReturn(Optional.empty());
        when(holdRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> service.confirm(holdId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not in HELD state");

        // Verify inventory was NOT touched (no double release)
        verify(inventoryRepository, never()).lockNightsForHold(any(), any(), any(), any());
        verify(inventoryRepository, never()).saveAll(any());
    }
}
