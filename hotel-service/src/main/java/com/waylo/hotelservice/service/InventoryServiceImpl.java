package com.waylo.hotelservice.service;

import com.waylo.hotelservice.domain.DailyInventory;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final DailyInventoryRepository dailyInventoryRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final HotelRoomTypeRepository hotelRoomTypeRepository;
    private final HotelRepository hotelRepository;
    private final Clock clock;

    public InventoryServiceImpl(DailyInventoryRepository dailyInventoryRepository,
                                InventoryAdjustmentRepository inventoryAdjustmentRepository,
                                HotelRoomTypeRepository hotelRoomTypeRepository,
                                HotelRepository hotelRepository,
                                Clock clock) {
        this.dailyInventoryRepository = dailyInventoryRepository;
        this.inventoryAdjustmentRepository = inventoryAdjustmentRepository;
        this.hotelRoomTypeRepository = hotelRoomTypeRepository;
        this.hotelRepository = hotelRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyInventoryResponse> getInventory(UUID hotelId, LocalDate from, LocalDate to, UUID roomTypeId) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("'from' and 'to' dates are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' date must not be after 'to' date");
        }
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        if (daysBetween > 366) {
            throw new IllegalArgumentException("Date range must be at most 366 days inclusive");
        }

        // Validate hotel exists
        if (!hotelRepository.existsById(hotelId)) {
            throw new ResourceNotFoundException("Hotel", "id", hotelId);
        }

        List<DailyInventory> rows;
        if (roomTypeId != null) {
            // Validate roomTypeId belongs to the hotel
            hotelRoomTypeRepository.findByIdAndHotelId(roomTypeId, hotelId)
                    .orElseThrow(() -> new ResourceNotFoundException("RoomType", "id", roomTypeId));

            rows = dailyInventoryRepository.findByHotelIdAndRoomTypeIdAndStayDateBetween(hotelId, roomTypeId, from, to);
        } else {
            rows = dailyInventoryRepository.findByHotelIdAndStayDateBetween(hotelId, from, to);
        }
        return rows.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<DailyInventoryResponse> updateInventory(UUID hotelId, UUID userId, InventoryUpdateRequest request) {
        // Validate date range per item
        LocalDate now = LocalDate.now(clock);
        LocalDate minDate = now.minusDays(1);
        LocalDate maxDate = now.plusDays(730);

        for (InventoryUpdateItem item : request.items()) {
            if (item.stayDate().isBefore(minDate) || item.stayDate().isAfter(maxDate)) {
                throw new BusinessRuleException("STAY_DATE_OUT_OF_RANGE",
                        "Stay date " + item.stayDate() + " must be between " + minDate + " and " + maxDate);
            }
        }

        // Validate hotel exists
        if (!hotelRepository.existsById(hotelId)) {
            throw new ResourceNotFoundException("Hotel", "id", hotelId);
        }

        // Check for duplicate (roomTypeId, stayDate) in request items
        Map<String, InventoryUpdateItem> itemMap = new HashMap<>();
        for (InventoryUpdateItem item : request.items()) {
            String key = item.roomTypeId() + "|" + item.stayDate();
            if (itemMap.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate item for roomTypeId=" + item.roomTypeId()
                        + " and stayDate=" + item.stayDate());
            }
            itemMap.put(key, item);
        }

        // Validate all roomTypeIds belong to the hotel
        List<UUID> roomTypeIds = request.items().stream()
                .map(InventoryUpdateItem::roomTypeId)
                .distinct()
                .collect(Collectors.toList());

        for (UUID roomTypeId : roomTypeIds) {
            hotelRoomTypeRepository.findByIdAndHotelId(roomTypeId, hotelId)
                    .orElseThrow(() -> new ResourceNotFoundException("RoomType", "id", roomTypeId));
        }

        // Lock existing rows in stable order
        LocalDate from = request.items().stream()
                .map(InventoryUpdateItem::stayDate)
                .min(LocalDate::compareTo)
                .orElse(now);
        LocalDate to = request.items().stream()
                .map(InventoryUpdateItem::stayDate)
                .max(LocalDate::compareTo)
                .orElse(now);

        List<DailyInventory> lockedRows = dailyInventoryRepository
                .findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(hotelId, roomTypeIds, from, to);

        // Build a map of existing rows by key
        Map<String, DailyInventory> existingMap = new HashMap<>();
        for (DailyInventory row : lockedRows) {
            existingMap.put(row.getRoomTypeId() + "|" + row.getStayDate(), row);
        }

        // Sort items by (roomTypeId, stayDate) for deterministic processing
        List<InventoryUpdateItem> sortedItems = request.items().stream()
                .sorted((a, b) -> {
                    int cmp = a.roomTypeId().compareTo(b.roomTypeId());
                    if (cmp != 0) return cmp;
                    return a.stayDate().compareTo(b.stayDate());
                })
                .collect(Collectors.toList());

        List<DailyInventory> savedRows = new ArrayList<>();
        List<InventoryAdjustment> adjustments = new ArrayList<>();
        List<DailyInventoryResponse> unchangedRows = new ArrayList<>();

        for (InventoryUpdateItem item : sortedItems) {
            String key = item.roomTypeId() + "|" + item.stayDate();
            DailyInventory row = existingMap.get(key);

            boolean isNew = (row == null);
            boolean changed = false;

            if (isNew) {
                // New row: allotment is required
                if (item.allotment() == null) {
                    throw new IllegalArgumentException("Allotment is required for a new inventory row");
                }

                // If expectedVersion is given on a non-existing row -> 409
                if (item.expectedVersion() != null) {
                    throw new ObjectOptimisticLockingFailureException(
                            DailyInventory.class,
                            "expectedVersion=" + item.expectedVersion() + " on non-existing row for roomTypeId=" + item.roomTypeId() + " stayDate=" + item.stayDate());
                }

                row = new DailyInventory();
                row.setHotelId(hotelId);
                row.setRoomTypeId(item.roomTypeId());
                row.setStayDate(item.stayDate());
                row.setAllotment(item.allotment());
                row.setOutOfService(item.outOfService() != null ? item.outOfService() : 0);
                row.setHeld(0);
                row.setConfirmed(0);
                row.setStopSell(item.stopSell() != null && item.stopSell());

                validateInventoryConstraints(row);
                changed = true;
                isNew = true;

            } else {
                // Existing row: check expectedVersion
                if (item.expectedVersion() != null && !item.expectedVersion().equals(row.getVersion())) {
                    throw new ObjectOptimisticLockingFailureException(
                            DailyInventory.class,
                            row.getId() != null ? row.getId().toString() : "unknown");
                }

                int oldAllotment = row.getAllotment();
                int oldOutOfService = row.getOutOfService();
                boolean oldStopSell = row.isStopSell();

                // Apply null-safe updates
                if (item.allotment() != null) {
                    row.setAllotment(item.allotment());
                }
                if (item.outOfService() != null) {
                    row.setOutOfService(item.outOfService());
                }
                if (item.stopSell() != null) {
                    row.setStopSell(item.stopSell());
                }

                validateInventoryConstraints(row);

                // Check if anything actually changed
                if (row.getAllotment() != oldAllotment
                        || row.getOutOfService() != oldOutOfService
                        || row.isStopSell() != oldStopSell) {
                    changed = true;
                    // Record adjustment with old/new values
                    InventoryAdjustment adjustment = new InventoryAdjustment();
                    adjustment.setHotelId(hotelId);
                    adjustment.setRoomTypeId(item.roomTypeId());
                    adjustment.setStayDate(item.stayDate());
                    adjustment.setActorUserId(userId);
                    adjustment.setReason(request.reason());
                    adjustment.setOldAllotment(oldAllotment);
                    adjustment.setNewAllotment(row.getAllotment());
                    adjustment.setOldOutOfService(oldOutOfService);
                    adjustment.setNewOutOfService(row.getOutOfService());
                    adjustment.setOldStopSell(oldStopSell);
                    adjustment.setNewStopSell(row.isStopSell());
                    adjustments.add(adjustment);
                }
            }

            if (changed || isNew) {
                if (isNew) {
                    // New rows must also produce an InventoryAdjustment
                    InventoryAdjustment adjustment = new InventoryAdjustment();
                    adjustment.setHotelId(hotelId);
                    adjustment.setRoomTypeId(item.roomTypeId());
                    adjustment.setStayDate(item.stayDate());
                    adjustment.setActorUserId(userId);
                    adjustment.setReason(request.reason());
                    adjustment.setOldAllotment(null);
                    adjustment.setNewAllotment(row.getAllotment());
                    adjustment.setOldOutOfService(null);
                    adjustment.setNewOutOfService(row.getOutOfService());
                    adjustment.setOldStopSell(null);
                    adjustment.setNewStopSell(row.isStopSell());
                    adjustments.add(adjustment);
                }
                savedRows.add(row);
            } else {
                // Unchanged existing row: return with original ID/version
                unchangedRows.add(toResponse(row));
            }
        }

        // Save all changed/new rows with flush so versions/updatedAt reflect persistent state
        if (!savedRows.isEmpty()) {
            dailyInventoryRepository.saveAllAndFlush(savedRows);
        }

        // Build responses in stable roomTypeId/stayDate order, one per item (both new and existing)
        List<DailyInventoryResponse> responses = new ArrayList<>(sortedItems.size());
        for (InventoryUpdateItem item : sortedItems) {
            String key = item.roomTypeId() + "|" + item.stayDate();
            DailyInventory row = existingMap.get(key);
            if (row != null) {
                responses.add(toResponse(row));
            } else {
                // New row: look up in savedRows
                for (DailyInventory sr : savedRows) {
                    if (sr.getRoomTypeId().equals(item.roomTypeId()) && sr.getStayDate().equals(item.stayDate())) {
                        responses.add(toResponse(sr));
                        break;
                    }
                }
            }
        }

        // Save all adjustments (append-only)
        if (!adjustments.isEmpty()) {
            inventoryAdjustmentRepository.saveAll(adjustments);
        }

        return responses;
    }

    private void validateInventoryConstraints(DailyInventory row) {
        // outOfService must not exceed allotment
        if (row.getOutOfService() > row.getAllotment()) {
            throw new BusinessRuleException("INVENTORY_BELOW_COMMITTED",
                    "outOfService (" + row.getOutOfService() + ") cannot exceed allotment (" + row.getAllotment() + ")");
        }

        // allotment - outOfService must be >= held + confirmed (never reduce sellable capacity below committed)
        int sellable = row.getAllotment() - row.getOutOfService();
        int committed = row.getHeld() + row.getConfirmed();
        if (sellable < committed) {
            throw new BusinessRuleException("INVENTORY_BELOW_COMMITTED",
                    "Available capacity (allotment - outOfService = " + sellable
                            + ") is less than committed (held + confirmed = " + committed + ")");
        }
    }

    private DailyInventoryResponse toResponse(DailyInventory row) {
        return new DailyInventoryResponse(
                row.getId(),
                row.getHotelId(),
                row.getRoomTypeId(),
                row.getStayDate(),
                row.getAllotment(),
                row.getOutOfService(),
                row.getHeld(),
                row.getConfirmed(),
                row.getAvailable(),
                row.isStopSell(),
                row.getVersion(),
                row.getUpdatedAt()
        );
    }
}
