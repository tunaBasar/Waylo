package com.waylo.hotelservice.service;

import com.waylo.hotelservice.dto.DailyInventoryResponse;
import com.waylo.hotelservice.dto.InventoryUpdateRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface InventoryService {

    List<DailyInventoryResponse> getInventory(UUID hotelId, LocalDate from, LocalDate to, UUID roomTypeId);

    List<DailyInventoryResponse> updateInventory(UUID hotelId, UUID userId, InventoryUpdateRequest request);
}
