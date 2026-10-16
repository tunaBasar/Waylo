package com.waylo.hotelservice.service;

import com.waylo.hotelservice.domain.DailyInventory;
import com.waylo.hotelservice.domain.HotelRoomType;
import com.waylo.hotelservice.domain.InventoryAdjustment;
import com.waylo.hotelservice.dto.DailyInventoryResponse;
import com.waylo.hotelservice.dto.InventoryUpdateItem;
import com.waylo.hotelservice.dto.InventoryUpdateRequest;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.DailyInventoryRepository;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.HotelRoomTypeRepository;
import com.waylo.hotelservice.repository.InventoryAdjustmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryServiceImplTest {

    private final UUID hotelId = UUID.randomUUID();
    private final UUID roomTypeId1 = UUID.randomUUID();
    private final UUID roomTypeId2 = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final LocalDate now = LocalDate.of(2026, 12, 25);
    private final Instant clockInstant = Instant.parse("2026-12-25T00:00:00Z");
    private final Clock clock = Clock.fixed(clockInstant, ZoneId.of("UTC"));

    @Mock
    private DailyInventoryRepository dailyInventoryRepository;
    @Mock
    private InventoryAdjustmentRepository inventoryAdjustmentRepository;
    @Mock
    private HotelRoomTypeRepository hotelRoomTypeRepository;
    @Mock
    private HotelRepository hotelRepository;

    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InventoryServiceImpl(
                dailyInventoryRepository,
                inventoryAdjustmentRepository,
                hotelRoomTypeRepository,
                hotelRepository,
                clock
        );
    }

    private DailyInventory createRow(UUID id, UUID hotelId, UUID roomTypeId, LocalDate date,
                                     int allotment, int outOfService, int held, int confirmed) {
        DailyInventory row = new DailyInventory();
        row.setId(id);
        row.setHotelId(hotelId);
        row.setRoomTypeId(roomTypeId);
        row.setStayDate(date);
        row.setAllotment(allotment);
        row.setOutOfService(outOfService);
        row.setHeld(held);
        row.setConfirmed(confirmed);
        row.setVersion(0L);
        row.setUpdatedAt(Instant.parse("2026-12-25T00:00:00Z"));
        return row;
    }

    // ---- getInventory tests ----

    @Test
    void getInventory_returnsRowsForDateRange() {
        DailyInventory row = createRow(UUID.randomUUID(), hotelId, roomTypeId1, LocalDate.of(2026, 12, 25), 100, 0, 5, 3);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(dailyInventoryRepository.findByHotelIdAndStayDateBetween(
                eq(hotelId), any(), any())).thenReturn(List.of(row));

        List<DailyInventoryResponse> result = service.getInventory(hotelId, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 30), null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).hotelId()).isEqualTo(hotelId);
        assertThat(result.get(0).available()).isEqualTo(92);
    }

    @Test
    void getInventory_unknownHotel_throws404() {
        when(hotelRepository.existsById(hotelId)).thenReturn(false);

        assertThatThrownBy(() -> service.getInventory(hotelId, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 30), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getInventory_filtersByRoomTypeId() {
        DailyInventory row = createRow(UUID.randomUUID(), hotelId, roomTypeId1, LocalDate.of(2026, 12, 25), 100, 0, 0, 0);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdAndStayDateBetween(
                eq(hotelId), eq(roomTypeId1), any(), any())).thenReturn(List.of(row));

        List<DailyInventoryResponse> result = service.getInventory(hotelId, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 30), roomTypeId1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).roomTypeId()).isEqualTo(roomTypeId1);
    }

    @Test
    void getInventory_roomTypeIdNotInHotel_throws404() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInventory(hotelId, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 30), roomTypeId1))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getInventory_nullFrom_throws400() {
        assertThatThrownBy(() -> service.getInventory(hotelId, null, LocalDate.of(2026, 12, 30), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from");
    }

    @Test
    void getInventory_nullTo_throws400() {
        assertThatThrownBy(() -> service.getInventory(hotelId, LocalDate.of(2026, 12, 20), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("to");
    }

    @Test
    void getInventory_fromAfterTo_throws400() {
        assertThatThrownBy(() -> service.getInventory(hotelId, LocalDate.of(2026, 12, 30), LocalDate.of(2026, 12, 20), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getInventory_rangeOver366Days_throws400() {
        assertThatThrownBy(() -> service.getInventory(hotelId, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 3), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- updateInventory tests ----

    @Test
    void updateInventory_newRow_requiresAllotment() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(any(), anyList(), any(), any()))
                .thenReturn(List.of());

        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), null, null, null, null);
        InventoryUpdateRequest request = new InventoryUpdateRequest("test", List.of(item));

        assertThatThrownBy(() -> service.updateInventory(hotelId, userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Allotment is required");
    }

    @Test
    void updateInventory_outOfServiceGreaterThanAllotment_throws422() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(any(), anyList(), any(), any()))
                .thenReturn(List.of());

        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 100, 200, null, null);
        InventoryUpdateRequest request = new InventoryUpdateRequest("test", List.of(item));

        assertThatThrownBy(() -> service.updateInventory(hotelId, userId, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode").isEqualTo("INVENTORY_BELOW_COMMITTED");
    }

    @Test
    void updateInventory_reduceBelowHeldConfirmed_throws422() {
        DailyInventory existing = createRow(UUID.randomUUID(), hotelId, roomTypeId1, LocalDate.of(2026, 12, 25), 100, 0, 60, 50);
        // sellable = 100 - 0 = 100, committed = 60 + 50 = 110 => fails
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(
                eq(hotelId), anyList(), any(), any())).thenReturn(List.of(existing));
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));

        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 90, 0, null, null);
        InventoryUpdateRequest request = new InventoryUpdateRequest("reduce", List.of(item));

        assertThatThrownBy(() -> service.updateInventory(hotelId, userId, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode").isEqualTo("INVENTORY_BELOW_COMMITTED");
    }

    @Test
    void updateInventory_versionMismatch_throwsObjectOptimisticLockingFailure() {
        DailyInventory existing = createRow(UUID.randomUUID(), hotelId, roomTypeId1, LocalDate.of(2026, 12, 25), 100, 0, 0, 0);
        existing.setVersion(2L);
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(
                eq(hotelId), anyList(), any(), any())).thenReturn(List.of(existing));
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));

        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 100, null, null, 5L);
        InventoryUpdateRequest request = new InventoryUpdateRequest("test", List.of(item));

        assertThatThrownBy(() -> service.updateInventory(hotelId, userId, request))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void updateInventory_newRow_expectedVersionOnNew_throwsObjectOptimisticLockingFailure() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(any(), anyList(), any(), any()))
                .thenReturn(List.of());

        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 100, null, null, 1L);
        InventoryUpdateRequest request = new InventoryUpdateRequest("test", List.of(item));

        assertThatThrownBy(() -> service.updateInventory(hotelId, userId, request))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void updateInventory_foreignRoomTypeId_throws404() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.empty());

        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 100, null, null, null);
        InventoryUpdateRequest request = new InventoryUpdateRequest("test", List.of(item));

        assertThatThrownBy(() -> service.updateInventory(hotelId, userId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateInventory_nonExistentHotel_throws404() {
        when(hotelRepository.existsById(hotelId)).thenReturn(false);

        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 100, null, null, null);
        InventoryUpdateRequest request = new InventoryUpdateRequest("test", List.of(item));

        assertThatThrownBy(() -> service.updateInventory(hotelId, userId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateInventory_duplicateItems_throws400() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        InventoryUpdateItem item1 = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 100, null, null, null);
        InventoryUpdateItem item2 = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 80, null, null, null);
        InventoryUpdateRequest request = new InventoryUpdateRequest("test", List.of(item1, item2));

        assertThatThrownBy(() -> service.updateInventory(hotelId, userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void updateInventory_unchangedItem_returnsRow_noAdjustment_noSave() {
        DailyInventory existing = createRow(UUID.randomUUID(), hotelId, roomTypeId1, LocalDate.of(2026, 12, 25), 100, 5, 0, 0);
        existing.setVersion(1L);
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(
                eq(hotelId), anyList(), any(), any())).thenReturn(List.of(existing));
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));

        // Sending null for all updatable fields = no change
        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), null, null, null, 1L);
        InventoryUpdateRequest request = new InventoryUpdateRequest("test", List.of(item));

        List<DailyInventoryResponse> result = service.updateInventory(hotelId, userId, request);

        // Unchanged item returns current row but does not save or audit
        assertThat(result).hasSize(1);
        assertThat(result.get(0).allotment()).isEqualTo(100);
        assertThat(result.get(0).outOfService()).isEqualTo(5);
        assertThat(result.get(0).version()).isEqualTo(1L);
        assertThat(result.get(0).id()).isEqualTo(existing.getId());
        verify(dailyInventoryRepository, never()).saveAllAndFlush(any());
        verify(inventoryAdjustmentRepository, never()).saveAll(any());
    }

    @Test
    void updateInventory_changedItem_createsAdjustmentWithOldNewValues() {
        DailyInventory existing = createRow(UUID.randomUUID(), hotelId, roomTypeId1, LocalDate.of(2026, 12, 25), 100, 5, 0, 0);
        existing.setVersion(1L);
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(
                eq(hotelId), anyList(), any(), any())).thenReturn(List.of(existing));
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(dailyInventoryRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<DailyInventory> saved = invocation.getArgument(0);
            saved.get(0).setId(UUID.randomUUID());
            saved.get(0).setVersion(2L);
            saved.get(0).setUpdatedAt(Instant.parse("2026-12-25T00:00:01Z"));
            return saved;
        });
        when(inventoryAdjustmentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 120, 10, null, 1L);
        InventoryUpdateRequest request = new InventoryUpdateRequest("renegotiation", List.of(item));

        List<DailyInventoryResponse> result = service.updateInventory(hotelId, userId, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).allotment()).isEqualTo(120);
        assertThat(result.get(0).outOfService()).isEqualTo(10);
        verify(inventoryAdjustmentRepository).saveAll(anyList());
    }

    @Test
    void updateInventory_yesterdayDate_isValid() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(any(), anyList(), any(), any()))
                .thenReturn(List.of());
        when(dailyInventoryRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<DailyInventory> saved = invocation.getArgument(0);
            saved.get(0).setId(UUID.randomUUID());
            return saved;
        });

        // Date is 2026-12-24 which is now.minusDays(1) - this should be valid
        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 24), 100, null, null, null);
        InventoryUpdateRequest request = new InventoryUpdateRequest("test", List.of(item));

        List<DailyInventoryResponse> result = service.updateInventory(hotelId, userId, request);
        assertThat(result).isNotEmpty();
    }

    @Test
    void updateInventory_stayDateTooFarInFuture_throws422() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));

        // 2029-01-01 is far beyond now + 730 days (maxDate = 2028-12-20)
        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2029, 1, 1), 100, null, null, null);
        InventoryUpdateRequest request = new InventoryUpdateRequest("test", List.of(item));

        assertThatThrownBy(() -> service.updateInventory(hotelId, userId, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode").isEqualTo("STAY_DATE_OUT_OF_RANGE");
    }

    @Test
    void updateInventory_stayDateBeforeMinDate_throws422() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));

        // 2026-12-22 is before now.minusDays(1) = 2026-12-24
        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 22), 100, null, null, null);
        InventoryUpdateRequest request = new InventoryUpdateRequest("test", List.of(item));

        assertThatThrownBy(() -> service.updateInventory(hotelId, userId, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode").isEqualTo("STAY_DATE_OUT_OF_RANGE");
    }

    @Test
    void updateInventory_multipleItems_inDateRange() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId2), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(any(), anyList(), any(), any()))
                .thenReturn(List.of());
        when(dailyInventoryRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<DailyInventory> saved = invocation.getArgument(0);
            saved.get(0).setId(UUID.randomUUID());
            saved.get(1).setId(UUID.randomUUID());
            return saved;
        });

        // Both dates are within range
        InventoryUpdateItem item1 = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 24), 100, null, null, null);
        InventoryUpdateItem item2 = new InventoryUpdateItem(roomTypeId2, LocalDate.of(2026, 12, 26), 80, null, null, null);
        InventoryUpdateRequest request = new InventoryUpdateRequest("batch", List.of(item1, item2));

        List<DailyInventoryResponse> result = service.updateInventory(hotelId, userId, request);
        assertThat(result).hasSize(2);
    }

    @Test
    void updateInventory_newItemOutsideRange_throws422() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId2), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));

        // item1 is in range, item2 is not (too far future)
        InventoryUpdateItem item1 = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 100, null, null, null);
        InventoryUpdateItem item2 = new InventoryUpdateItem(roomTypeId2, LocalDate.of(2030, 1, 1), 80, null, null, null);
        InventoryUpdateRequest request = new InventoryUpdateRequest("batch", List.of(item1, item2));

        assertThatThrownBy(() -> service.updateInventory(hotelId, userId, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode").isEqualTo("STAY_DATE_OUT_OF_RANGE");
    }

    @Test
    void updateInventory_existingStopSellUpdate_worksWithoutAllotment() {
        // Existing row stopSell-only update: no allotment in request, should work
        DailyInventory existing = createRow(UUID.randomUUID(), hotelId, roomTypeId1, LocalDate.of(2026, 12, 25), 100, 5, 0, 0);
        existing.setVersion(1L);
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(
                eq(hotelId), anyList(), any(), any())).thenReturn(List.of(existing));
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(inventoryAdjustmentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), null, null, true, 1L);
        InventoryUpdateRequest request = new InventoryUpdateRequest("stop-sell", List.of(item));

        List<DailyInventoryResponse> result = service.updateInventory(hotelId, userId, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).stopSell()).isTrue();
        assertThat(result.get(0).allotment()).isEqualTo(100);
        assertThat(result.get(0).outOfService()).isEqualTo(5);
        verify(inventoryAdjustmentRepository).saveAll(anyList());
    }

    @Test
    void updateInventory_getAvailable_math() {
        DailyInventory row = new DailyInventory();
        row.setAllotment(500);
        row.setOutOfService(50);
        row.setHeld(100);
        row.setConfirmed(50);

        assertThat(row.getAvailable()).isEqualTo(300);
    }

    @Test
    void updateInventory_returnsUpdatedRows() {
        DailyInventory existing = createRow(UUID.randomUUID(), hotelId, roomTypeId1, LocalDate.of(2026, 12, 25), 100, 0, 0, 0);
        existing.setVersion(1L);
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(
                eq(hotelId), anyList(), any(), any())).thenReturn(List.of(existing));
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(dailyInventoryRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<DailyInventory> saved = invocation.getArgument(0);
            saved.get(0).setId(UUID.randomUUID());
            saved.get(0).setVersion(2L);
            saved.get(0).setUpdatedAt(Instant.parse("2026-12-25T00:00:01Z"));
            return saved;
        });

        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 150, 20, true, 1L);
        InventoryUpdateRequest request = new InventoryUpdateRequest("change", List.of(item));

        List<DailyInventoryResponse> result = service.updateInventory(hotelId, userId, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).allotment()).isEqualTo(150);
        assertThat(result.get(0).outOfService()).isEqualTo(20);
        assertThat(result.get(0).stopSell()).isTrue();
        assertThat(result.get(0).available()).isEqualTo(130);
    }

    @Test
    void updateInventory_existingRow_allotmentChanged_othersUnchanged() {
        DailyInventory existing = createRow(UUID.randomUUID(), hotelId, roomTypeId1, LocalDate.of(2026, 12, 25), 100, 10, 5, 3);
        existing.setVersion(2L);
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(
                eq(hotelId), anyList(), any(), any())).thenReturn(List.of(existing));
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(dailyInventoryRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<DailyInventory> saved = invocation.getArgument(0);
            saved.get(0).setId(UUID.randomUUID());
            saved.get(0).setVersion(3L);
            saved.get(0).setUpdatedAt(Instant.parse("2026-12-25T00:00:01Z"));
            return saved;
        });

        // Only change allotment, outOfService and stopSell are null
        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 120, null, null, 2L);
        InventoryUpdateRequest request = new InventoryUpdateRequest("update allotment", List.of(item));

        List<DailyInventoryResponse> result = service.updateInventory(hotelId, userId, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).allotment()).isEqualTo(120);
        assertThat(result.get(0).outOfService()).isEqualTo(10); // unchanged
        assertThat(result.get(0).held()).isEqualTo(5);
        assertThat(result.get(0).confirmed()).isEqualTo(3);
    }

    @Test
    void updateInventory_newRow_success() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(any(), anyList(), any(), any()))
                .thenReturn(List.of());
        when(dailyInventoryRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<DailyInventory> saved = invocation.getArgument(0);
            saved.get(0).setId(UUID.randomUUID());
            saved.get(0).setVersion(0L);
            return saved;
        });

        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 100, 5, true, null);
        InventoryUpdateRequest request = new InventoryUpdateRequest("initial", List.of(item));

        List<DailyInventoryResponse> result = service.updateInventory(hotelId, userId, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).allotment()).isEqualTo(100);
        assertThat(result.get(0).outOfService()).isEqualTo(5);
        assertThat(result.get(0).stopSell()).isTrue();
        assertThat(result.get(0).held()).isEqualTo(0);
        assertThat(result.get(0).confirmed()).isEqualTo(0);
        assertThat(result.get(0).available()).isEqualTo(95);
    }

    @Test
    void updateInventory_multipleItems_allOrNothing() {
        DailyInventory existing1 = createRow(UUID.randomUUID(), hotelId, roomTypeId1, LocalDate.of(2026, 12, 25), 100, 0, 0, 0);
        existing1.setVersion(1L);
        DailyInventory existing2 = createRow(UUID.randomUUID(), hotelId, roomTypeId2, LocalDate.of(2026, 12, 26), 80, 0, 0, 0);
        existing2.setVersion(1L);

        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(
                eq(hotelId), anyList(), any(), any())).thenReturn(List.of(existing1, existing2));
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId2), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(dailyInventoryRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<DailyInventory> saved = invocation.getArgument(0);
            saved.get(0).setId(UUID.randomUUID());
            saved.get(1).setId(UUID.randomUUID());
            return saved;
        });
        when(inventoryAdjustmentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryUpdateItem item1 = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 110, null, null, 1L);
        InventoryUpdateItem item2 = new InventoryUpdateItem(roomTypeId2, LocalDate.of(2026, 12, 26), 90, 5, null, 1L);
        InventoryUpdateRequest request = new InventoryUpdateRequest("batch", List.of(item1, item2));

        List<DailyInventoryResponse> result = service.updateInventory(hotelId, userId, request);

        assertThat(result).hasSize(2);
        verify(inventoryAdjustmentRepository).saveAll(anyList());
    }

    @Test
    void updateInventory_newItem_auditNullOldValues() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(any(), anyList(), any(), any()))
                .thenReturn(List.of());
        when(dailyInventoryRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<DailyInventory> saved = invocation.getArgument(0);
            saved.get(0).setId(UUID.randomUUID());
            return saved;
        });
        when(inventoryAdjustmentRepository.saveAll(any())).thenAnswer(invocation -> {
            List<InventoryAdjustment> adj = invocation.getArgument(0);
            // Verify null old values in the audit record
            assertThat(adj.get(0).getOldAllotment()).isNull();
            assertThat(adj.get(0).getOldOutOfService()).isNull();
            assertThat(adj.get(0).getOldStopSell()).isNull();
            return adj;
        });

        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 100, 5, true, null);
        InventoryUpdateRequest request = new InventoryUpdateRequest("initial", List.of(item));

        service.updateInventory(hotelId, userId, request);

        verify(inventoryAdjustmentRepository).saveAll(anyList());
    }

    @Test
    void updateInventory_flushedVersionTimestamps() {
        DailyInventory existing = createRow(UUID.randomUUID(), hotelId, roomTypeId1, LocalDate.of(2026, 12, 25), 100, 0, 0, 0);
        existing.setVersion(1L);
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(
                eq(hotelId), anyList(), any(), any())).thenReturn(List.of(existing));
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(dailyInventoryRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<DailyInventory> saved = invocation.getArgument(0);
            saved.get(0).setId(UUID.randomUUID());
            saved.get(0).setVersion(2L);
            saved.get(0).setUpdatedAt(Instant.parse("2026-12-25T00:00:01Z"));
            return saved;
        });

        InventoryUpdateItem item = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 120, 10, null, 1L);
        InventoryUpdateRequest request = new InventoryUpdateRequest("change", List.of(item));

        List<DailyInventoryResponse> result = service.updateInventory(hotelId, userId, request);

        assertThat(result).hasSize(1);
        // Response reflects flushed version/timestamps
        assertThat(result.get(0).version()).isEqualTo(2L);
        assertThat(result.get(0).updatedAt()).isEqualTo(Instant.parse("2026-12-25T00:00:01Z"));
    }

    @Test
    void updateInventory_lockingMethod_hasPessimisticWriteAnnotation() throws Exception {
        // Verify the repository method has @Lock(PESSIMISTIC_WRITE)
        var method = DailyInventoryRepository.class.getMethod(
                "findByHotelIdAndRoomTypeIdsAndDateRangeWithLock",
                UUID.class, List.class, LocalDate.class, LocalDate.class);
        var lockAnnotation = method.getAnnotation(org.springframework.data.jpa.repository.Lock.class);
        assertThat(lockAnnotation).isNotNull();
        assertThat(lockAnnotation.value()).isEqualTo(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void updateInventory_responseOrder_isRoomTypeIdStayDateSorted() {
        // Two items: different room types, verify response has both items in sorted order
        DailyInventory existing = createRow(UUID.randomUUID(), hotelId, roomTypeId1, LocalDate.of(2026, 12, 25), 100, 0, 0, 0);
        existing.setVersion(1L);
        DailyInventory anotherExisting = createRow(UUID.randomUUID(), hotelId, roomTypeId2, LocalDate.of(2026, 12, 26), 80, 0, 0, 0);
        anotherExisting.setVersion(1L);

        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(
                eq(hotelId), anyList(), any(), any())).thenReturn(List.of(existing, anotherExisting));
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId2), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(dailyInventoryRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<DailyInventory> saved = invocation.getArgument(0);
            saved.get(0).setId(UUID.randomUUID());
            saved.get(0).setVersion(2L);
            saved.get(0).setUpdatedAt(Instant.parse("2026-12-25T00:00:01Z"));
            return saved;
        });
        when(inventoryAdjustmentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Room types in any order - service sorts internally by roomTypeId
        InventoryUpdateItem item1 = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 110, null, null, 1L);
        InventoryUpdateItem item2 = new InventoryUpdateItem(roomTypeId2, LocalDate.of(2026, 12, 26), 90, 5, null, 1L);
        InventoryUpdateRequest request = new InventoryUpdateRequest("batch", List.of(item1, item2));

        List<DailyInventoryResponse> result = service.updateInventory(hotelId, userId, request);

        // Response has exactly 2 items
        assertThat(result).hasSize(2);
        // Response order matches sorted item order (by roomTypeId, stayDate)
        UUID first = result.get(0).roomTypeId();
        UUID second = result.get(1).roomTypeId();
        // Both room types present
        assertThat(Set.of(first, second)).isEqualTo(Set.of(roomTypeId1, roomTypeId2));
        // Verify they are in sorted order (first <= second)
        assertThat(first.compareTo(second)).isLessThanOrEqualTo(0);
    }

    @Test
    void updateInventory_mixedChangedUnchanged_responseStableOrder() {
        // Mixed: one changed, one unchanged - both should be in the same stable sorted order
        DailyInventory changed = createRow(UUID.randomUUID(), hotelId, roomTypeId1, LocalDate.of(2026, 12, 25), 100, 0, 0, 0);
        changed.setVersion(1L);
        DailyInventory unchanged = createRow(UUID.randomUUID(), hotelId, roomTypeId2, LocalDate.of(2026, 12, 26), 80, 0, 0, 0);
        unchanged.setVersion(1L);

        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(
                eq(hotelId), anyList(), any(), any())).thenReturn(List.of(changed, unchanged));
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId1), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(hotelRoomTypeRepository.findByIdAndHotelId(eq(roomTypeId2), eq(hotelId)))
                .thenReturn(Optional.of(new HotelRoomType()));
        when(dailyInventoryRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<DailyInventory> saved = invocation.getArgument(0);
            saved.get(0).setId(UUID.randomUUID());
            saved.get(0).setVersion(2L);
            saved.get(0).setUpdatedAt(Instant.parse("2026-12-25T00:00:01Z"));
            return saved;
        });

        // roomTypeId1 changes, roomTypeId2 is no-op
        InventoryUpdateItem item1 = new InventoryUpdateItem(roomTypeId1, LocalDate.of(2026, 12, 25), 110, null, null, 1L);
        InventoryUpdateItem item2 = new InventoryUpdateItem(roomTypeId2, LocalDate.of(2026, 12, 26), null, null, null, 1L);
        InventoryUpdateRequest request = new InventoryUpdateRequest("mixed", List.of(item1, item2));

        List<DailyInventoryResponse> result = service.updateInventory(hotelId, userId, request);

        assertThat(result).hasSize(2);
        // Service sorts items by (roomTypeId, stayDate); roomTypeId1/roomTypeId2 are random UUIDs,
        // so assert against their actual UUID order rather than assuming roomTypeId1 < roomTypeId2.
        boolean rt1First = roomTypeId1.compareTo(roomTypeId2) <= 0;
        DailyInventoryResponse first = rt1First ? result.get(0) : result.get(1);
        DailyInventoryResponse second = rt1First ? result.get(1) : result.get(0);
        assertThat(first.roomTypeId()).isEqualTo(roomTypeId1);
        assertThat(second.roomTypeId()).isEqualTo(roomTypeId2);
        assertThat(first.allotment()).isEqualTo(110); // changed
        assertThat(second.allotment()).isEqualTo(80);   // unchanged
    }
}
