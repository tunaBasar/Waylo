package com.waylo.paymentservice.service;

import com.waylo.paymentservice.client.OrderClient;
import com.waylo.paymentservice.controller.AdminReconciliationController;
import com.waylo.paymentservice.domain.CommissionRate;
import com.waylo.paymentservice.domain.EscrowItem;
import com.waylo.paymentservice.domain.EscrowStatus;
import com.waylo.paymentservice.domain.LedgerEntry;
import com.waylo.paymentservice.domain.PaymentIntent;
import com.waylo.paymentservice.domain.PaymentIntentStatus;
import com.waylo.paymentservice.domain.PaymentReconciliation;
import com.waylo.paymentservice.domain.ReconciliationStatus;
import com.waylo.paymentservice.dto.IntentResponse;
import com.waylo.paymentservice.exception.AccessDeniedException;
import com.waylo.paymentservice.exception.ConflictException;
import com.waylo.paymentservice.provider.PaymentProvider;
import com.waylo.paymentservice.provider.TestPaymentProvider;
import com.waylo.paymentservice.repository.CommissionRateRepository;
import com.waylo.paymentservice.repository.EscrowItemRepository;
import com.waylo.paymentservice.repository.LedgerEntryRepository;
import com.waylo.paymentservice.repository.PaymentIntentRepository;
import com.waylo.paymentservice.repository.PaymentReconciliationRepository;
import com.waylo.paymentservice.repository.SupplierPayoutAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** RM-27 B: PaymentReconciliationJob with in-memory repositories and the real PaymentIntentService, TEST provider, fixed Clock. */
@ExtendWith(MockitoExtension.class)
class PaymentReconciliationJobTest {

    @Mock PaymentIntentRepository intents;
    @Mock EscrowItemRepository escrows;
    @Mock SupplierPayoutAccountRepository payoutAccounts;
    @Mock CommissionRateRepository rates;
    @Mock LedgerEntryRepository ledgerRepo;
    @Mock PaymentReconciliationRepository reconciliations;
    @Mock OrderClient orderClient;
    @Mock PlatformTransactionManager txManager;

    final List<PaymentIntent> intentStore = new ArrayList<>();
    final List<EscrowItem> escrowStore = new ArrayList<>();
    final List<LedgerEntry> ledgerStore = new ArrayList<>();
    final List<PaymentReconciliation> logStore = new ArrayList<>();

    PaymentProvider provider = spy(new TestPaymentProvider());
    PaymentIntentService intentService;
    PaymentReconciliationJob job;
    Instant now = Instant.parse("2026-11-24T12:00:00Z");

    final UUID customer = UUID.randomUUID();
    final UUID orderId = UUID.randomUUID();
    final UUID itemId = UUID.randomUUID();
    final UUID supplier = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(intents.findByOrderId(any())).thenAnswer(i -> intentStore.stream().filter(p -> p.getOrderId().equals(i.getArgument(0))).findFirst());
        lenient().when(intents.findByIdForUpdate(any())).thenAnswer(i -> intentStore.stream().filter(p -> p.getId().equals(i.getArgument(0))).findFirst());
        lenient().when(intents.saveAndFlush(any())).thenAnswer(i -> {
            PaymentIntent p = i.getArgument(0);
            if (!intentStore.contains(p)) intentStore.add(p);
            return p;
        });
        // candidates: AUTHORIZED, no escrow, not in manual review (age is re-checked by the job through updatedAt)
        lenient().when(intents.findAuthorizedWithoutEscrow(any(), any())).thenAnswer(i -> intentStore.stream()
                .filter(p -> p.getStatus() == PaymentIntentStatus.AUTHORIZED && escrowStore.stream().noneMatch(e -> e.getPaymentIntentId().equals(p.getId()))
                        && logStore.stream().noneMatch(r -> r.getPaymentIntentId().equals(p.getId()) && r.getStatus() == ReconciliationStatus.MANUAL_REVIEW))
                .map(PaymentIntent::getId).toList());
        lenient().when(escrows.save(any())).thenAnswer(i -> { escrowStore.add(i.getArgument(0)); return i.getArgument(0); });
        lenient().when(escrows.findByOrderItemId(any())).thenAnswer(i -> escrowStore.stream().filter(e -> e.getOrderItemId().equals(i.getArgument(0))).findFirst());
        lenient().when(escrows.findByPaymentIntentId(any())).thenAnswer(i -> escrowStore.stream().filter(e -> e.getPaymentIntentId().equals(i.getArgument(0))).toList());
        lenient().when(ledgerRepo.save(any())).thenAnswer(i -> { ledgerStore.add(i.getArgument(0)); return i.getArgument(0); });
        lenient().when(ledgerRepo.findByOrderIdOrderByOccurredAtAscEntryIdAsc(any())).thenAnswer(i -> new ArrayList<>(ledgerStore));
        lenient().when(reconciliations.save(any())).thenAnswer(i -> { logStore.add(i.getArgument(0)); return i.getArgument(0); });
        lenient().when(payoutAccounts.findByOrganizationId(any())).thenReturn(Optional.empty());
        CommissionRate rate = new CommissionRate();
        rate.setRate(new BigDecimal("0.1000"));
        lenient().when(rates.findFirstByVerticalAndSupplierOrganizationIdAndValidFromLessThanEqualOrderByValidFromDescCreatedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(rates.findFirstByVerticalAndSupplierOrganizationIdIsNullAndValidFromLessThanEqualOrderByValidFromDescCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(rate));
        intentService = new PaymentIntentService(intents, escrows, payoutAccounts, new CommissionService(rates),
                new LedgerService(ledgerRepo), orderClient, provider);
        job = new PaymentReconciliationJob(intents, reconciliations, intentService, orderClient, provider,
                Clock.fixed(now, ZoneOffset.UTC), txManager, 5, 50);
    }

    OrderClient.OrderDto order(String status) {
        return new OrderClient.OrderDto(orderId, customer, status, "TRY", new BigDecimal("1000.00"),
                List.of(new OrderClient.OrderItemDto(itemId, "HOTEL", supplier, new BigDecimal("1000.00"), "TRY")), List.of());
    }

    void givenOrder(String status) {
        lenient().when(orderClient.getOrder(orderId)).thenReturn(new OrderClient.Envelope<>(true, order(status), null));
        lenient().when(orderClient.markPaid(orderId)).thenReturn(new OrderClient.Envelope<>(true, order(status), null));
    }

    /** A normally confirmed payment, then the ambiguous aftermath: escrow and ledger lost, intent still AUTHORIZED. */
    PaymentIntent capturedThenLostEscrow(String orderStatusNow) {
        givenOrder("PENDING");
        IntentResponse created = intentService.createIntent(customer, "CUSTOMER", orderId);
        intentService.confirm(customer, "CUSTOMER", created.id(), "TEST_OK");
        PaymentIntent intent = intentStore.get(0);
        intent.setUpdatedAt(now.minusSeconds(600)); // older than the 5 minute threshold
        escrowStore.clear();
        ledgerStore.clear();
        givenOrder(orderStatusNow);
        return intent;
    }

    // --- branches ---

    @Test
    void orderConfirmed_repairsEscrowAndLedger_equalToConfirm() {
        givenOrder("PENDING");
        IntentResponse created = intentService.createIntent(customer, "CUSTOMER", orderId);
        intentService.confirm(customer, "CUSTOMER", created.id(), "TEST_OK");
        EscrowItem original = escrowStore.get(0);
        int originalLedgerRows = ledgerStore.size();
        PaymentIntent intent = intentStore.get(0);
        intent.setUpdatedAt(now.minusSeconds(600));
        escrowStore.clear();
        ledgerStore.clear();
        givenOrder("CONFIRMED");

        assertThat(job.reconcile()).isEqualTo(1);

        assertThat(escrowStore).hasSize(1);
        EscrowItem repaired = escrowStore.get(0);
        assertThat(repaired.getOrderItemId()).isEqualTo(original.getOrderItemId());
        assertThat(repaired.getStatus()).isEqualTo(EscrowStatus.HELD);
        assertThat(repaired.getGrossAmount()).isEqualByComparingTo(original.getGrossAmount());
        assertThat(repaired.getChargedAmount()).isEqualByComparingTo(original.getChargedAmount());
        assertThat(repaired.getCommissionRate()).isEqualByComparingTo(original.getCommissionRate());
        assertThat(repaired.getCommissionAmount()).isEqualByComparingTo(original.getCommissionAmount());
        assertThat(repaired.getSupplierAmount()).isEqualByComparingTo(original.getSupplierAmount());
        assertThat(repaired.getReleaseTrigger()).isEqualTo(original.getReleaseTrigger());
        assertThat(repaired.getSupplierOrganizationId()).isEqualTo(original.getSupplierOrganizationId());
        assertThat(ledgerStore).hasSize(originalLedgerRows);
        BigDecimal balance = ledgerStore.stream().map(e -> e.getDebitAmount().subtract(e.getCreditAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(balance).isEqualByComparingTo("0");
        assertThat(intent.getStatus()).isEqualTo(PaymentIntentStatus.AUTHORIZED);
        assertThat(logStore).singleElement().satisfies(r -> assertThat(r.getStatus()).isEqualTo(ReconciliationStatus.ESCROW_REPAIRED));
        verify(provider, never()).cancel(any());
    }

    @Test
    void orderPendingExpiredOrFailed_cancelsProviderPaymentAndIntent() {
        for (String status : new String[]{"PENDING", "EXPIRED", "FAILED"}) {
            intentStore.clear(); escrowStore.clear(); ledgerStore.clear(); logStore.clear();
            reset(provider);
            PaymentIntent intent = capturedThenLostEscrow(status);
            String providerPaymentId = intent.getProviderPaymentId();

            assertThat(job.reconcile()).as(status).isEqualTo(1);

            verify(provider).cancel(providerPaymentId);
            assertThat(intent.getStatus()).isEqualTo(PaymentIntentStatus.CANCELLED);
            assertThat(escrowStore).isEmpty();
            assertThat(logStore).singleElement().satisfies(r -> {
                assertThat(r.getStatus()).isEqualTo(ReconciliationStatus.PROVIDER_CANCELLED);
                assertThat(r.getOrderStatus()).isEqualTo(status);
            });
        }
    }

    @Test
    void anyOtherOrderState_isLoggedForManualReview_andNothingChanges() {
        PaymentIntent intent = capturedThenLostEscrow("CANCELLED");
        reset(provider);

        assertThat(job.reconcile()).isEqualTo(1);

        assertThat(intent.getStatus()).isEqualTo(PaymentIntentStatus.AUTHORIZED);
        assertThat(escrowStore).isEmpty();
        assertThat(ledgerStore).isEmpty();
        verify(provider, never()).cancel(any());
        assertThat(logStore).singleElement().satisfies(r -> assertThat(r.getStatus()).isEqualTo(ReconciliationStatus.MANUAL_REVIEW));
    }

    // --- idempotency ---

    @Test
    void secondRun_changesNothing_inEveryBranch() {
        capturedThenLostEscrow("CONFIRMED");
        assertThat(job.reconcile()).isEqualTo(1);
        int escrow = escrowStore.size(), ledger = ledgerStore.size(), log = logStore.size();
        assertThat(job.reconcile()).isZero();
        assertThat(escrowStore).hasSize(escrow);
        assertThat(ledgerStore).hasSize(ledger);
        assertThat(logStore).hasSize(log);

        intentStore.clear(); escrowStore.clear(); ledgerStore.clear(); logStore.clear(); reset(provider);
        capturedThenLostEscrow("EXPIRED");
        assertThat(job.reconcile()).isEqualTo(1);
        assertThat(job.reconcile()).isZero();
        verify(provider, times(1)).cancel(any());
        assertThat(logStore).hasSize(1);

        intentStore.clear(); escrowStore.clear(); ledgerStore.clear(); logStore.clear();
        capturedThenLostEscrow("CANCELLED");
        assertThat(job.reconcile()).isEqualTo(1);
        assertThat(job.reconcile()).isZero();
        assertThat(logStore).hasSize(1);
    }

    @Test
    void repairEscrow_calledTwice_doesNotDuplicateEscrowOrLedger() {
        PaymentIntent intent = capturedThenLostEscrow("CONFIRMED");
        intentService.repairEscrow(intent, order("CONFIRMED"));
        intentService.repairEscrow(intent, order("CONFIRMED"));
        assertThat(escrowStore).hasSize(1);
        assertThat(ledgerStore).hasSize(2);
    }

    // --- never touch healthy intents ---

    @Test
    void intentWithEscrow_orNotAuthorized_isNeverTouched() {
        givenOrder("PENDING");
        IntentResponse created = intentService.createIntent(customer, "CUSTOMER", orderId);
        // REQUIRES_PAYMENT: not a candidate
        assertThat(job.reconcile()).isZero();
        intentService.confirm(customer, "CUSTOMER", created.id(), "TEST_OK");
        intentStore.get(0).setUpdatedAt(now.minusSeconds(600));
        // AUTHORIZED with escrow: not a candidate
        assertThat(job.reconcile()).isZero();
        assertThat(escrowStore).hasSize(1);
        verify(provider, never()).cancel(any());
        assertThat(logStore).isEmpty();
    }

    @Test
    void tooYoungIntent_isSkippedByTheRecheck() {
        PaymentIntent intent = capturedThenLostEscrow("CONFIRMED");
        // the clock is before the intent's last update: not old enough
        PaymentReconciliationJob early = new PaymentReconciliationJob(intents, reconciliations, intentService, orderClient, provider,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), txManager, 5, 50);
        assertThat(early.reconcile()).isZero();
        assertThat(escrowStore).isEmpty();
        assertThat(intent.getStatus()).isEqualTo(PaymentIntentStatus.AUTHORIZED);
    }

    // --- 409 ORDER_EXPIRED handling and admin endpoint ---

    @Test
    void confirm_whenOrderSaysExpired_cancelsProviderPaymentAndRollsBack() {
        givenOrder("PENDING");
        IntentResponse created = intentService.createIntent(customer, "CUSTOMER", orderId);
        when(orderClient.markPaid(orderId)).thenThrow(new ConflictException("ORDER_EXPIRED", "expired"));
        assertThatThrownBy(() -> intentService.confirm(customer, "CUSTOMER", created.id(), "TEST_OK"))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("ORDER_EXPIRED"));
        verify(provider).cancel(any());
    }

    @Test
    void adminEndpoint_isAdminOnly_andPages() {
        var controller = new AdminReconciliationController(reconciliations);
        UUID admin = UUID.randomUUID();
        for (String role : new String[]{"CUSTOMER", "PARTNER", null}) {
            assertThatThrownBy(() -> controller.list(admin, role, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
        }
        when(reconciliations.search(eq(ReconciliationStatus.MANUAL_REVIEW), any())).thenReturn(new PageImpl<>(List.of()));
        var res = controller.list(admin, "ADMIN", ReconciliationStatus.MANUAL_REVIEW, 0, 20);
        assertThat(res.getBody().data().items()).isEmpty();
        verify(reconciliations).search(eq(ReconciliationStatus.MANUAL_REVIEW), any());
    }
}
