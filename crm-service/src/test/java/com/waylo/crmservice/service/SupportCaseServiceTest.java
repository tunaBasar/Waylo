package com.waylo.crmservice.service;

import com.waylo.crmservice.client.AuthAccessClient;
import com.waylo.crmservice.client.OrderClient;
import com.waylo.crmservice.domain.SupportCase;
import com.waylo.crmservice.domain.SupportMessage;
import com.waylo.crmservice.dto.SupportDtos.CaseResponse;
import com.waylo.crmservice.dto.SupportDtos.OpenCaseRequest;
import com.waylo.crmservice.exception.AccessDeniedException;
import com.waylo.crmservice.exception.BusinessRuleException;
import com.waylo.crmservice.exception.ResourceNotFoundException;
import com.waylo.crmservice.repository.SupportCaseRepository;
import com.waylo.crmservice.repository.SupportMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SupportCaseServiceTest {

    private final SupportCaseRepository cases = mock(SupportCaseRepository.class);
    private final SupportMessageRepository messages = mock(SupportMessageRepository.class);
    private final OrderClient orders = mock(OrderClient.class);
    private final AuthAccessClient access = mock(AuthAccessClient.class);
    private final SupportCaseService service = new SupportCaseService(cases, messages, orders, access);

    private final UUID customerA = UUID.randomUUID();
    private final UUID customerB = UUID.randomUUID();
    private final UUID orgA = UUID.randomUUID();
    private final UUID orgB = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final List<SupportMessage> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(cases.save(any(SupportCase.class))).thenAnswer(i -> i.getArgument(0));
        when(messages.save(any(SupportMessage.class))).thenAnswer(i -> {
            stored.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(messages.findByCaseIdOrderByCreatedAtAsc(any())).thenAnswer(i -> stored);
        when(orders.getOrder(orderId)).thenReturn(new OrderClient.Envelope<>(true,
                new OrderClient.OrderDto(orderId, customerA, "CONFIRMED", List.of(new OrderClient.ItemDto(itemId, orgA))), null));
    }

    private SupportCase caseOf(UUID customer, UUID org) {
        SupportCase c = new SupportCase(customer, orderId, itemId, org, "subject", Instant.now());
        when(cases.findById(c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    private void allow(UUID user, UUID org, boolean allowed) {
        when(access.check(user, org, "RESERVATION_READ")).thenReturn(new AuthAccessClient.AccessDecision(allowed, List.of()));
    }

    // ---- ownership ----

    @Test
    void openOnOwnOrderStoresSupplierAndFirstMessage() {
        CaseResponse r = service.open(customerA, new OpenCaseRequest(orderId, itemId, " Late ", " Hello "));
        assertThat(r.status()).isEqualTo(SupportCase.Status.OPEN);
        assertThat(r.supplierOrganizationId()).isEqualTo(orgA);
        assertThat(r.subject()).isEqualTo("Late");
        assertThat(r.messages()).hasSize(1);
        assertThat(r.messages().get(0).authorRole()).isEqualTo(SupportMessage.Role.CUSTOMER);
    }

    @Test
    void openOnForeignOrderIsNotFoundAndNothingIsSaved() {
        assertThatThrownBy(() -> service.open(customerB, new OpenCaseRequest(orderId, null, "s", "m")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(cases, never()).save(any());
        verify(messages, never()).save(any());
    }

    @Test
    void openWithItemOfAnotherOrderIsNotFound() {
        assertThatThrownBy(() -> service.open(customerA, new OpenCaseRequest(orderId, UUID.randomUUID(), "s", "m")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void customerCannotReadOrCloseForeignCase() {
        SupportCase c = caseOf(customerA, orgA);
        assertThatThrownBy(() -> service.getMine(customerB, c.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.closeMine(customerB, c.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.customerReply(customerB, c.getId(), "x")).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- partner org isolation ----

    @Test
    void partnerWithoutPermissionIsDenied() {
        UUID partner = UUID.randomUUID();
        allow(partner, orgB, false);
        assertThatThrownBy(() -> service.listForOrganization(partner, orgB)).isInstanceOf(AccessDeniedException.class);
        verify(cases, never()).findTop200BySupplierOrganizationIdOrderByCreatedAtDesc(any());
    }

    @Test
    void partnerOfOtherOrganizationCannotAnswerCase() {
        UUID partnerB = UUID.randomUUID();
        SupportCase c = caseOf(customerA, orgA);
        allow(partnerB, orgB, true);   // allowed in ITS org, but the case belongs to orgA
        assertThatThrownBy(() -> service.partnerReply(partnerB, orgB, c.getId(), "hi")).isInstanceOf(ResourceNotFoundException.class);
        assertThat(c.getStatus()).isEqualTo(SupportCase.Status.OPEN);
    }

    @Test
    void partnerReplySetsAnswered() {
        UUID partnerA = UUID.randomUUID();
        SupportCase c = caseOf(customerA, orgA);
        allow(partnerA, orgA, true);
        CaseResponse r = service.partnerReply(partnerA, orgA, c.getId(), "we look into it");
        assertThat(r.status()).isEqualTo(SupportCase.Status.ANSWERED);
        assertThat(stored.get(0).getAuthorRole()).isEqualTo(SupportMessage.Role.PARTNER);
    }

    // ---- status transitions ----

    @Test
    void customerFollowUpReopensAnsweredCase() {
        SupportCase c = caseOf(customerA, orgA);
        c.onMessage(SupportMessage.Role.PARTNER, Instant.now());
        assertThat(c.getStatus()).isEqualTo(SupportCase.Status.ANSWERED);
        assertThat(service.customerReply(customerA, c.getId(), "thanks, one more thing").status()).isEqualTo(SupportCase.Status.OPEN);
    }

    @Test
    void closedCaseRejectsMessagesAndStaysClosed() {
        SupportCase c = caseOf(customerA, orgA);
        assertThat(service.closeMine(customerA, c.getId()).status()).isEqualTo(SupportCase.Status.CLOSED);
        assertThatThrownBy(() -> service.customerReply(customerA, c.getId(), "x"))
                .isInstanceOf(BusinessRuleException.class).extracting("ruleCode").isEqualTo("CASE_CLOSED");
        assertThatThrownBy(() -> service.adminReply(UUID.randomUUID(), c.getId(), "x")).isInstanceOf(BusinessRuleException.class);
        assertThat(service.closeMine(customerA, c.getId()).status()).isEqualTo(SupportCase.Status.CLOSED);   // idempotent
    }

    @Test
    void adminReplyAnswersAndAdminCloseCloses() {
        SupportCase c = caseOf(customerA, null);
        assertThat(service.adminReply(UUID.randomUUID(), c.getId(), "admin here").status()).isEqualTo(SupportCase.Status.ANSWERED);
        assertThat(service.adminClose(c.getId()).status()).isEqualTo(SupportCase.Status.CLOSED);
    }

    // ---- append-only ----

    @Test
    void messageEntityHasNoSettersAndNoUpdatableColumns() {
        for (Method m : SupportMessage.class.getDeclaredMethods()) {
            assertThat(m.getName()).doesNotStartWith("set");
        }
        for (var f : SupportMessage.class.getDeclaredFields()) {
            var col = f.getAnnotation(jakarta.persistence.Column.class);
            if (col != null) assertThat(col.updatable()).as(f.getName()).isFalse();
        }
    }

    @Test
    void serviceNeverDeletesMessages() {
        SupportCase c = caseOf(customerA, orgA);
        service.customerReply(customerA, c.getId(), "one");
        service.closeMine(customerA, c.getId());
        verify(messages, never()).delete(any());
        verify(messages, never()).deleteAll();
        verify(messages, never()).deleteById(any());
    }

    @Test
    void migrationInstallsAppendOnlyTrigger() throws Exception {
        String sql = new String(getClass().getResourceAsStream("/db/migration/V2__support_cases.sql").readAllBytes());
        assertThat(sql).contains("BEFORE UPDATE OR DELETE ON support_messages").contains("BEFORE TRUNCATE ON support_messages");
    }
}
