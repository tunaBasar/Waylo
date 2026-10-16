package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.DailyInventoryResponse;
import com.waylo.hotelservice.dto.InventoryUpdateItem;
import com.waylo.hotelservice.dto.InventoryUpdateRequest;
import com.waylo.hotelservice.exception.AccessDeniedException;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.security.HotelAccessGuard;
import com.waylo.hotelservice.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    private final InventoryService inventoryService = mock(InventoryService.class);
    private final HotelAccessGuard accessGuard = mock(HotelAccessGuard.class);
    @InjectMocks
    private InventoryController controller;

    private final UUID hotelId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private DailyInventoryResponse buildResponse(UUID id) {
        return new DailyInventoryResponse(
                id, hotelId, UUID.randomUUID(),
                LocalDate.of(2026, 12, 25), 100, 0, 5, 3,
                92, false, 0L, java.time.Instant.now()
        );
    }

    // ---- GET tests ----

    @Test
    void get_guardCalledBeforeService() {
        when(inventoryService.getInventory(eq(hotelId), any(), any(), any())).thenReturn(List.of());

        controller.getInventory(userId, hotelId, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 30), null);

        verify(accessGuard).requireRoomManage(userId, hotelId);
    }

    @Test
    void get_serviceNotCalledWhenGuardThrows() {
        doThrow(new AccessDeniedException("Not allowed")).when(accessGuard).requireRoomManage(userId, hotelId);

        assertThatThrownBy(() ->
                controller.getInventory(userId, hotelId, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 30), null))
                .isInstanceOf(AccessDeniedException.class);

        verify(inventoryService, never()).getInventory(any(), any(), any(), any());
    }

    @Test
    void get_guardCalledBeforeRangeChecks() {
        assertThatThrownBy(() ->
                controller.getInventory(userId, hotelId, LocalDate.of(2026, 12, 30), LocalDate.of(2026, 12, 20), null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(accessGuard).requireRoomManage(userId, hotelId);
        verify(inventoryService, never()).getInventory(any(), any(), any(), any());
    }

    @Test
    void get_fromAfterTo_throws400() {
        assertThatThrownBy(() ->
                controller.getInventory(userId, hotelId, LocalDate.of(2026, 12, 30), LocalDate.of(2026, 12, 20), null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(inventoryService, never()).getInventory(any(), any(), any(), any());
    }

    @Test
    void get_rangeOver366Days_throws400() {
        assertThatThrownBy(() ->
                controller.getInventory(userId, hotelId, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 3), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void get_returns200WithRoomTypeId() {
        DailyInventoryResponse response = buildResponse(UUID.randomUUID());
        when(inventoryService.getInventory(eq(hotelId), any(), any(), any())).thenReturn(List.of(response));

        ResponseEntity<ApiResponse<List<DailyInventoryResponse>>> result =
                controller.getInventory(userId, hotelId, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 30),
                        UUID.randomUUID());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).hasSize(1);
        verify(accessGuard).requireRoomManage(userId, hotelId);
    }

    // ---- PUT tests ----

    @Test
    void put_guardCalledBeforeService() {
        List<InventoryUpdateItem> items = List.of(
                new InventoryUpdateItem(UUID.randomUUID(), LocalDate.of(2026, 12, 25), 100, null, null, null)
        );
        InventoryUpdateRequest request = new InventoryUpdateRequest("reason", items);
        when(inventoryService.updateInventory(eq(hotelId), eq(userId), any())).thenReturn(List.of());

        controller.updateInventory(userId, hotelId, request);

        verify(accessGuard).requireRoomManage(userId, hotelId);
    }

    @Test
    void put_serviceNotCalledWhenGuardThrows() {
        doThrow(new AccessDeniedException("Not allowed")).when(accessGuard).requireRoomManage(userId, hotelId);
        List<InventoryUpdateItem> items = List.of(
                new InventoryUpdateItem(UUID.randomUUID(), LocalDate.of(2026, 12, 25), 100, null, null, null)
        );
        InventoryUpdateRequest request = new InventoryUpdateRequest("reason", items);

        assertThatThrownBy(() -> controller.updateInventory(userId, hotelId, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(inventoryService, never()).updateInventory(any(), any(), any());
    }

    @Test
    void put_guardCalledBeforeEmptyItemsCheck() {
        doThrow(new AccessDeniedException("Not allowed")).when(accessGuard).requireRoomManage(userId, hotelId);
        InventoryUpdateRequest request = new InventoryUpdateRequest("reason", List.of());

        assertThatThrownBy(() -> controller.updateInventory(userId, hotelId, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(inventoryService, never()).updateInventory(any(), any(), any());
    }

    @Test
    void put_emptyItems_returnsBadRequest() {
        doNothing().when(accessGuard).requireRoomManage(userId, hotelId);
        InventoryUpdateRequest request = new InventoryUpdateRequest("reason", List.of());

        assertThatThrownBy(() -> controller.updateInventory(userId, hotelId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one item");

        verify(inventoryService, never()).updateInventory(any(), any(), any());
    }

    @Test
    void put_returns200WithUpdatedData() {
        List<InventoryUpdateItem> items = List.of(
                new InventoryUpdateItem(UUID.randomUUID(), LocalDate.of(2026, 12, 25), 100, null, null, null)
        );
        InventoryUpdateRequest request = new InventoryUpdateRequest("reason", items);
        DailyInventoryResponse response = buildResponse(UUID.randomUUID());
        when(inventoryService.updateInventory(eq(hotelId), eq(userId), any())).thenReturn(List.of(response));

        ResponseEntity<ApiResponse<List<DailyInventoryResponse>>> result =
                controller.updateInventory(userId, hotelId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).hasSize(1);
        assertThat(result.getBody().message()).isEqualTo("Inventory updated");
    }

    @Test
    void put_partialExistingUpdate_noAllotmentRequired() {
        // Controller should delegate to service; it does NOT validate allotment at controller level.
        // This is an existing-row stopSell-only update - no allotment in request.
        List<InventoryUpdateItem> items = List.of(
                new InventoryUpdateItem(UUID.randomUUID(), LocalDate.of(2026, 12, 25), null, null, true, null)
        );
        InventoryUpdateRequest request = new InventoryUpdateRequest("stop-sell", items);
        DailyInventoryResponse response = buildResponse(UUID.randomUUID());
        when(inventoryService.updateInventory(eq(hotelId), eq(userId), any())).thenReturn(List.of(response));

        ResponseEntity<ApiResponse<List<DailyInventoryResponse>>> result =
                controller.updateInventory(userId, hotelId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        verify(inventoryService).updateInventory(eq(hotelId), eq(userId), any());
    }

    @Test
    void put_serviceExceptionPropagates() {
        List<InventoryUpdateItem> items = List.of(
                new InventoryUpdateItem(UUID.randomUUID(), LocalDate.of(2026, 12, 25), 100, null, null, null)
        );
        InventoryUpdateRequest request = new InventoryUpdateRequest("reason", items);
        when(inventoryService.updateInventory(eq(hotelId), eq(userId), any()))
                .thenThrow(new BusinessRuleException("INVENTORY_BELOW_COMMITTED", "not enough capacity"));

        assertThatThrownBy(() -> controller.updateInventory(userId, hotelId, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not enough capacity");

        verify(accessGuard).requireRoomManage(userId, hotelId);
    }
}
