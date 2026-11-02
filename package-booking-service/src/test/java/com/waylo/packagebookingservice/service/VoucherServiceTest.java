package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderItemVertical;
import com.waylo.packagebookingservice.domain.Voucher;
import com.waylo.packagebookingservice.domain.VoucherStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.VoucherRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VoucherServiceTest {

    VoucherRepository repo = mock(VoucherRepository.class);
    OrderItemRepository itemRepo = mock(OrderItemRepository.class);
    VoucherService service;

    @BeforeEach
    void setUp() {
        service = new VoucherService(repo, itemRepo, "unit-test-secret-unit-test-secret-1234");
    }

    private OrderItem hotelItem() {
        OrderItem i = new OrderItem();
        i.setId(UUID.randomUUID());
        i.setVertical(OrderItemVertical.HOTEL);
        i.setCheckIn(LocalDate.of(2026, 12, 1));
        i.setCheckOut(LocalDate.of(2026, 12, 3));
        i.setDescription("Deluxe");
        i.setUnitAmount(new BigDecimal("100.00"));
        i.setQuantity(1);
        i.setCurrency("USD");
        return i;
    }

    @Test
    void createForItems_storesOnlyHash_andWindowIsStayInIstanbul() {
        OrderItem item = hotelItem();
        service.createForItems(List.of(item));
        ArgumentCaptor<Voucher> cap = ArgumentCaptor.forClass(Voucher.class);
        verify(repo).save(cap.capture());
        Voucher v = cap.getValue();
        String code = service.payload(v.getId()).split(":")[2];
        assertThat(v.getCodeHash()).hasSize(64).isEqualTo(VoucherService.hash(code)).doesNotContain(code);
        assertThat(v.getStatus()).isEqualTo(VoucherStatus.ACTIVE);
        assertThat(v.getValidFrom()).isEqualTo(Instant.parse("2026-11-30T21:00:00Z")); // 00:00 +03
        assertThat(v.getValidUntil()).isEqualTo(Instant.parse("2026-12-03T09:00:00Z")); // 12:00 +03
        assertThat(code).hasSize(22);
    }

    @Test
    void createForItems_isIdempotent() {
        OrderItem item = hotelItem();
        when(repo.findByOrderItemId(item.getId())).thenReturn(Optional.of(new Voucher()));
        service.createForItems(List.of(item));
        verify(repo, never()).save(any());
    }

    @Test
    void payload_format_andStable() {
        UUID id = UUID.randomUUID();
        assertThat(service.payload(id)).matches("WAYLO1:" + id + ":[A-Za-z0-9_-]{22}").isEqualTo(service.payload(id));
    }

    @Test
    void listForOrder_hidesPayloadWhenNotActive() {
        OrderItem item = hotelItem();
        when(itemRepo.findByOrderId(any())).thenReturn(List.of(item));
        Voucher v = new Voucher();
        v.setId(UUID.randomUUID());
        v.setOrderItemId(item.getId());
        v.setStatus(VoucherStatus.VOID);
        v.setValidFrom(Instant.now());
        v.setValidUntil(Instant.now());
        when(repo.findByOrderItemIdIn(any())).thenReturn(List.of(v));
        assertThat(service.listForOrder(UUID.randomUUID()).get(0).payload()).isNull();
        v.setStatus(VoucherStatus.ACTIVE);
        assertThat(service.listForOrder(UUID.randomUUID()).get(0).payload()).startsWith("WAYLO1:");
    }

    @Test
    void voidForOrder_voidsActiveOnly() {
        OrderItem item = hotelItem();
        when(itemRepo.findByOrderId(any())).thenReturn(List.of(item));
        Voucher active = new Voucher();
        Voucher redeemed = new Voucher();
        redeemed.setStatus(VoucherStatus.REDEEMED);
        when(repo.findByOrderItemIdIn(any())).thenReturn(List.of(active, redeemed));
        service.voidForOrder(UUID.randomUUID());
        assertThat(active.getStatus()).isEqualTo(VoucherStatus.VOID);
        assertThat(redeemed.getStatus()).isEqualTo(VoucherStatus.REDEEMED);
    }

    @Test
    void flightWindowIsThreeHoursBeforeToOneHourAfterDeparture() {
        OrderItem item = new OrderItem();
        item.setVertical(com.waylo.packagebookingservice.domain.OrderItemVertical.FLIGHT);
        java.time.Instant dep = java.time.Instant.parse("2026-11-10T06:30:00Z");
        item.setDepartureAt(dep);
        java.time.Instant[] w = VoucherService.window(item);
        org.junit.jupiter.api.Assertions.assertEquals(dep.minusSeconds(3 * 3600), w[0]);
        org.junit.jupiter.api.Assertions.assertEquals(dep.plusSeconds(3600), w[1]);
    }

    @Test
    void busWindowIsTwoHoursBeforeToOneHourAfterBoarding() {
        OrderItem item = new OrderItem();
        item.setVertical(com.waylo.packagebookingservice.domain.OrderItemVertical.BUS);
        item.setPickupAt(java.time.LocalDateTime.of(2027, 2, 2, 22, 0));
        java.time.Instant boarding = java.time.Instant.parse("2027-02-02T19:00:00Z");
        java.time.Instant[] w = VoucherService.window(item);
        org.junit.jupiter.api.Assertions.assertEquals(boarding.minusSeconds(2 * 3600), w[0]);
        org.junit.jupiter.api.Assertions.assertEquals(boarding.plusSeconds(3600), w[1]);
    }
}
