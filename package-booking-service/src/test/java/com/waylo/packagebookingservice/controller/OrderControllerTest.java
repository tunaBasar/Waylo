package com.waylo.packagebookingservice.controller;

import com.waylo.packagebookingservice.domain.OrderItemVertical;
import com.waylo.packagebookingservice.domain.OrderStatus;
import com.waylo.packagebookingservice.dto.ApiResponse;
import com.waylo.packagebookingservice.dto.OrderItemResponse;
import com.waylo.packagebookingservice.dto.OrderResponse;
import com.waylo.packagebookingservice.exception.AccessDeniedException;
import com.waylo.packagebookingservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;
    @InjectMocks
    private OrderController controller;

    private final UUID userId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    private OrderResponse buildResponse() {
        OrderItemResponse item = new OrderItemResponse(
                UUID.randomUUID(), orderId, OrderItemVertical.HOTEL, UUID.randomUUID(), UUID.randomUUID(), null,
                "Deluxe room", new BigDecimal("100.00"), 2, new BigDecimal("200.00"), "USD", Instant.now());
        return new OrderResponse(orderId, userId, OrderStatus.PENDING, "USD", null,
                new BigDecimal("200.00"), null, 0L, List.of(item), Instant.now(), Instant.now(), null);
    }

    @Test
    void createHotel_returns201() {
        var req = new com.waylo.packagebookingservice.dto.HotelOrderCreateRequest(UUID.randomUUID(), null);
        when(orderService.createHotelOrder(userId, req)).thenReturn(buildResponse());
        assertThat(controller.createHotel(userId, "USER", req).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void createHotel_admin_forbiddenWithCode() {
        var req = new com.waylo.packagebookingservice.dto.HotelOrderCreateRequest(UUID.randomUUID(), null);
        assertThatThrownBy(() -> controller.createHotel(userId, "ADMIN", req))
                .isInstanceOfSatisfying(AccessDeniedException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ADMIN_CANNOT_PURCHASE"));
        verifyNoInteractions(orderService);
    }

    @Test
    void getById_returns200() {
        OrderResponse response = buildResponse();
        when(orderService.getById(userId, orderId)).thenReturn(response);

        ResponseEntity<ApiResponse<OrderResponse>> result = controller.getById(userId, orderId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data().id()).isEqualTo(orderId);
    }

    @Test
    void getById_otherCustomer_propagatesAccessDenied() {
        when(orderService.getById(userId, orderId)).thenThrow(new AccessDeniedException("not owner"));

        assertThatThrownBy(() -> controller.getById(userId, orderId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listOwn_returns200() {
        when(orderService.listOwn(userId)).thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<OrderResponse>>> result = controller.listOwn(userId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).hasSize(1);
    }

    @Test
    void cancel_returns200WithExpectedVersion() {
        OrderResponse cancelled = new OrderResponse(orderId, userId, OrderStatus.CANCELLED, "USD", null,
                new BigDecimal("200.00"), null, 1L, List.of(), Instant.now(), Instant.now(), null);
        when(orderService.cancel(userId, orderId, 0L)).thenReturn(cancelled);

        ResponseEntity<ApiResponse<OrderResponse>> result = controller.cancel(userId, orderId, 0L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data().status()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderService).cancel(userId, orderId, 0L);
    }
}
