package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.client.AuthAccessClient;
import com.waylo.packagebookingservice.domain.OrderStatus;
import com.waylo.packagebookingservice.dto.PartnerOrderItemResponse;
import com.waylo.packagebookingservice.exception.AccessDeniedException;
import com.waylo.packagebookingservice.exception.AuthorizationUnavailableException;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class PartnerOrderServiceTest {

    private final OrderItemRepository repo = mock(OrderItemRepository.class);
    private final AuthAccessClient auth = mock(AuthAccessClient.class);
    private final PartnerOrderService service = new PartnerOrderService(repo, auth);

    private final UUID user = UUID.randomUUID();
    private final UUID org = UUID.randomUUID();
    private final UUID otherOrg = UUID.randomUUID();

    private PartnerOrderItemResponse row() {
        return new PartnerOrderItemResponse(UUID.randomUUID(), UUID.randomUUID(), "CONFIRMED", Instant.now(),
                "Deluxe room", new BigDecimal("200.00"), "USD", UUID.randomUUID());
    }

    @Test
    void member_seesOwnOrgItemsOnly() {
        when(auth.check(user, org, "RESERVATION_READ")).thenReturn(new AuthAccessClient.AccessDecision(true, List.of("HOTEL_STAFF")));
        when(repo.findPartnerItems(eq(org), eq(List.of(OrderStatus.CONFIRMED)), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row()), PageRequest.of(0, 20), 1));

        var page = service.list(user, org, OrderStatus.CONFIRMED, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 0, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isEqualTo(1);
        verify(repo).findPartnerItems(eq(org), eq(List.of(OrderStatus.CONFIRMED)),
                eq(Instant.parse("2026-01-01T00:00:00Z")), eq(Instant.parse("2026-02-01T00:00:00Z")), any(Pageable.class));
        verify(repo, never()).findPartnerItems(eq(otherOrg), any(), any(), any(), any());
    }

    @Test
    void otherOrgOrNonMember_isForbidden_andNothingQueried() {
        when(auth.check(user, otherOrg, "RESERVATION_READ")).thenReturn(new AuthAccessClient.AccessDecision(false, List.of()));
        assertThatThrownBy(() -> service.list(user, otherOrg, null, null, null, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repo);
    }

    @Test
    void nullDecision_isForbidden() {
        assertThatThrownBy(() -> service.list(user, org, null, null, null, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repo);
    }

    @Test
    void authDown_failsClosed() {
        when(auth.check(any(), any(), any())).thenThrow(new RuntimeException("connection refused"));
        assertThatThrownBy(() -> service.list(user, org, null, null, null, 0, 20))
                .isInstanceOf(AuthorizationUnavailableException.class);
        verifyNoInteractions(repo);
    }

    @Test
    void missingUser_isUnauthenticated() {
        assertThatThrownBy(() -> service.list(null, org, null, null, null, 0, 20))
                .isInstanceOf(com.waylo.packagebookingservice.exception.AuthenticationRequiredException.class);
        verifyNoInteractions(auth, repo);
    }

    @Test
    void pageSizeCappedAt100() {
        when(auth.check(user, org, "RESERVATION_READ")).thenReturn(new AuthAccessClient.AccessDecision(true, List.of()));
        when(repo.findPartnerItems(any(), any(), any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.list(user, org, null, null, null, -3, 5000);
        var captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findPartnerItems(any(), any(), any(), any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getPageNumber()).isZero();
    }

    @Test
    void responseJson_hasNoPiiFields() {
        String json = JsonMapper.builder().build().writeValueAsString(row()).toLowerCase();
        for (String forbidden : new String[]{"email", "phone", "customer", "passport", "payment", "notes", "name"}) {
            assertThat(json).doesNotContain(forbidden);
        }
        assertThat(json).contains("fulfillmentref", "amount", "currency", "description", "orderstatus");
    }
}
