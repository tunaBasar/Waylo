package com.waylo.crmservice.service;

import com.waylo.crmservice.client.AuthAccessClient;
import com.waylo.crmservice.client.OrderClient;
import com.waylo.crmservice.domain.SupportCase;
import com.waylo.crmservice.domain.SupportMessage;
import com.waylo.crmservice.dto.SupportDtos.CaseResponse;
import com.waylo.crmservice.dto.SupportDtos.OpenCaseRequest;
import com.waylo.crmservice.exception.AccessDeniedException;
import com.waylo.crmservice.exception.ResourceNotFoundException;
import com.waylo.crmservice.event.OutboxWriter;
import com.waylo.crmservice.event.SupportCaseUpdatedEvent;
import com.waylo.crmservice.exception.UpstreamUnavailableException;
import com.waylo.crmservice.repository.SupportCaseRepository;
import com.waylo.crmservice.repository.SupportMessageRepository;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** RM-36. Customers only ever touch cases of their own orders, partners only cases of their organization, admins all. */
@Service
public class SupportCaseService {

    public static final String PERMISSION = "RESERVATION_READ";

    private final SupportCaseRepository cases;
    private final SupportMessageRepository messages;
    private final OrderClient orderClient;
    private final AuthAccessClient accessClient;
    private final OutboxWriter outbox;

    public SupportCaseService(SupportCaseRepository cases, SupportMessageRepository messages,
                              OrderClient orderClient, AuthAccessClient accessClient, OutboxWriter outbox) {
        this.outbox = outbox;
        this.cases = cases;
        this.messages = messages;
        this.orderClient = orderClient;
        this.accessClient = accessClient;
    }

    // ---- customer ----

    @Transactional
    public CaseResponse open(UUID customerId, OpenCaseRequest req) {
        OrderClient.OrderDto order = loadOrder(req.orderId());
        if (order == null || !customerId.equals(order.customerId())) throw notFound("Order", req.orderId());
        List<OrderClient.ItemDto> items = order.items() == null ? List.of() : order.items();
        UUID supplier;
        if (req.orderItemId() != null) {
            supplier = items.stream().filter(i -> req.orderItemId().equals(i.orderItemId())).findFirst()
                    .orElseThrow(() -> notFound("OrderItem", req.orderItemId())).supplierOrganizationId();
        } else {
            Set<UUID> suppliers = items.stream().map(OrderClient.ItemDto::supplierOrganizationId).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            supplier = suppliers.size() == 1 ? suppliers.iterator().next() : null;
        }
        Instant now = Instant.now();
        SupportCase c = cases.save(new SupportCase(customerId, req.orderId(), req.orderItemId(), supplier, req.subject().trim(), now));
        SupportMessage m = messages.save(new SupportMessage(c.getId(), SupportMessage.Role.CUSTOMER, customerId, req.message().trim(), now));
        emit(c, SupportCaseUpdatedEvent.Kind.CREATED, SupportCaseUpdatedEvent.AuthorRole.CUSTOMER);
        return CaseResponse.of(c, List.of(m));
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> listMine(UUID customerId) {
        return cases.findTop200ByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(c -> CaseResponse.of(c, null)).toList();
    }

    @Transactional(readOnly = true)
    public CaseResponse getMine(UUID customerId, UUID caseId) {
        return detail(ownCase(customerId, caseId));
    }

    @Transactional
    public CaseResponse customerReply(UUID customerId, UUID caseId, String body) {
        return reply(ownCase(customerId, caseId), SupportMessage.Role.CUSTOMER, customerId, body);
    }

    @Transactional
    public CaseResponse closeMine(UUID customerId, UUID caseId) {
        return close(ownCase(customerId, caseId), null);
    }

    // ---- partner ----

    @Transactional(readOnly = true)
    public List<CaseResponse> listForOrganization(UUID userId, UUID organizationId) {
        requireOrganizationRead(userId, organizationId);
        return cases.findTop200BySupplierOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(c -> CaseResponse.of(c, null)).toList();
    }

    @Transactional(readOnly = true)
    public CaseResponse getForOrganization(UUID userId, UUID organizationId, UUID caseId) {
        requireOrganizationRead(userId, organizationId);
        return detail(organizationCase(organizationId, caseId));
    }

    @Transactional
    public CaseResponse partnerReply(UUID userId, UUID organizationId, UUID caseId, String body) {
        requireOrganizationRead(userId, organizationId);
        return reply(organizationCase(organizationId, caseId), SupportMessage.Role.PARTNER, userId, body);
    }

    // ---- admin (role checked by the controller) ----

    @Transactional(readOnly = true)
    public List<CaseResponse> listAll(SupportCase.Status status) {
        List<SupportCase> list = status == null ? cases.findTop200ByOrderByCreatedAtDesc()
                : cases.findTop200ByStatusOrderByCreatedAtDesc(status);
        return list.stream().map(c -> CaseResponse.of(c, null)).toList();
    }

    @Transactional(readOnly = true)
    public CaseResponse getAny(UUID caseId) {
        return detail(anyCase(caseId));
    }

    @Transactional
    public CaseResponse adminReply(UUID adminId, UUID caseId, String body) {
        return reply(anyCase(caseId), SupportMessage.Role.ADMIN, adminId, body);
    }

    @Transactional
    public CaseResponse adminClose(UUID caseId) {
        return close(anyCase(caseId), SupportCaseUpdatedEvent.AuthorRole.ADMIN);
    }

    // ---- internals ----

    private CaseResponse reply(SupportCase c, SupportMessage.Role role, UUID authorId, String body) {
        Instant now = Instant.now();
        c.onMessage(role, now);   // throws CASE_CLOSED
        cases.save(c);
        messages.save(new SupportMessage(c.getId(), role, authorId, body.trim(), now));
        if (role != SupportMessage.Role.CUSTOMER) {
            emit(c, SupportCaseUpdatedEvent.Kind.ANSWERED, SupportCaseUpdatedEvent.AuthorRole.valueOf(role.name()));
        } else {
            emit(c, SupportCaseUpdatedEvent.Kind.CUSTOMER_MESSAGE, SupportCaseUpdatedEvent.AuthorRole.CUSTOMER);
        }
        return detail(c);
    }

    /** adminRole is null for the customer's own close, which sends no e-mail. */
    private CaseResponse close(SupportCase c, SupportCaseUpdatedEvent.AuthorRole adminRole) {
        boolean wasOpen = c.getStatus() != SupportCase.Status.CLOSED;
        c.close(Instant.now());
        cases.save(c);
        if (adminRole != null && wasOpen) emit(c, SupportCaseUpdatedEvent.Kind.CLOSED, adminRole);
        return detail(c);
    }

    /** Same transaction as the message/close (RM-36 e-mail via outbox). */
    private void emit(SupportCase c, SupportCaseUpdatedEvent.Kind kind, SupportCaseUpdatedEvent.AuthorRole role) {
        outbox.write("SUPPORT_CASE", c.getId().toString(), "support-events", c.getId().toString(),
                new SupportCaseUpdatedEvent(c.getId(), c.getCustomerId(), c.getOrderId(), c.getSubject(), kind, role,
                        c.getSupplierOrganizationId()));
    }

    private CaseResponse detail(SupportCase c) {
        return CaseResponse.of(c, messages.findByCaseIdOrderByCreatedAtAsc(c.getId()));
    }

    /** Foreign and unknown cases are indistinguishable (404). */
    private SupportCase ownCase(UUID customerId, UUID caseId) {
        return cases.findById(caseId).filter(c -> c.getCustomerId().equals(customerId)).orElseThrow(() -> notFound("SupportCase", caseId));
    }

    private SupportCase organizationCase(UUID organizationId, UUID caseId) {
        return cases.findById(caseId).filter(c -> organizationId.equals(c.getSupplierOrganizationId()))
                .orElseThrow(() -> notFound("SupportCase", caseId));
    }

    private SupportCase anyCase(UUID caseId) {
        return cases.findById(caseId).orElseThrow(() -> notFound("SupportCase", caseId));
    }

    private void requireOrganizationRead(UUID userId, UUID organizationId) {
        AuthAccessClient.AccessDecision d;
        try {
            d = accessClient.check(userId, organizationId, PERMISSION);
        } catch (FeignException e) {
            throw new UpstreamUnavailableException("Access check unavailable");
        }
        if (d == null || !d.allowed()) throw new AccessDeniedException("Not allowed to access support cases of this organization");
    }

    private OrderClient.OrderDto loadOrder(UUID orderId) {
        try {
            OrderClient.Envelope<OrderClient.OrderDto> env = orderClient.getOrder(orderId);
            return env == null ? null : env.data();
        } catch (FeignException.NotFound e) {
            return null;
        } catch (FeignException e) {
            throw new UpstreamUnavailableException("Order service unavailable");
        }
    }

    private static ResourceNotFoundException notFound(String what, UUID id) {
        return new ResourceNotFoundException(what, "id", id);
    }
}
