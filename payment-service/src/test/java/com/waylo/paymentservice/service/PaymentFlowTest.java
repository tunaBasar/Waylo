package com.waylo.paymentservice.service;

import com.waylo.paymentservice.client.OrderClient;
import com.waylo.paymentservice.domain.EscrowItem;
import com.waylo.paymentservice.domain.EscrowStatus;
import com.waylo.paymentservice.domain.LedgerEntry;
import com.waylo.paymentservice.domain.PaymentIntent;
import com.waylo.paymentservice.domain.PaymentIntentStatus;
import com.waylo.paymentservice.dto.IntentResponse;
import com.waylo.paymentservice.exception.AccessDeniedException;
import com.waylo.paymentservice.exception.BusinessRuleException;
import com.waylo.paymentservice.provider.PaymentProvider;
import com.waylo.paymentservice.provider.TestPaymentProvider;
import com.waylo.paymentservice.repository.CommissionRateRepository;
import com.waylo.paymentservice.repository.EscrowItemRepository;
import com.waylo.paymentservice.repository.LedgerEntryRepository;
import com.waylo.paymentservice.repository.PaymentIntentRepository;
import com.waylo.paymentservice.repository.SupplierPayoutAccountRepository;
import com.waylo.paymentservice.domain.CommissionRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Services wired for real around in-memory fakes of the repositories; only the order client is a mock. */
@ExtendWith(MockitoExtension.class)
class PaymentFlowTest {

    @Mock PaymentIntentRepository intents;
    @Mock EscrowItemRepository escrows;
    @Mock SupplierPayoutAccountRepository payoutAccounts;
    @Mock CommissionRateRepository rates;
    @Mock LedgerEntryRepository ledgerRepo;
    @Mock OrderClient orderClient;

    final List<PaymentIntent> intentStore = new ArrayList<>();
    final List<EscrowItem> escrowStore = new ArrayList<>();
    final List<LedgerEntry> ledgerStore = new ArrayList<>();

    PaymentProvider provider = spy(new TestPaymentProvider());
    PaymentIntentService intentService;
    EscrowService escrowService;
    LedgerService ledger;

    final UUID customer = UUID.randomUUID();
    final UUID orderId = UUID.randomUUID();
    final UUID itemId = UUID.randomUUID();
    final UUID item2Id = UUID.randomUUID();
    final UUID supplier = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(intents.findByOrderId(any())).thenAnswer(i -> intentStore.stream().filter(p -> p.getOrderId().equals(i.getArgument(0))).findFirst());
        lenient().when(intents.findByIdForUpdate(any())).thenAnswer(i -> intentStore.stream().filter(p -> p.getId().equals(i.getArgument(0))).findFirst());
        lenient().when(intents.saveAndFlush(any())).thenAnswer(i -> {
            PaymentIntent p = i.getArgument(0);
            if (!intentStore.contains(p)) intentStore.add(p);
            return p;
        });
        lenient().when(escrows.save(any())).thenAnswer(i -> { escrowStore.add(i.getArgument(0)); return i.getArgument(0); });
        lenient().when(escrows.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(escrows.findByOrderItemIdForUpdate(any())).thenAnswer(i -> escrowStore.stream().filter(e -> e.getOrderItemId().equals(i.getArgument(0))).findFirst());
        lenient().when(escrows.findByOrderItemId(any())).thenAnswer(i -> escrowStore.stream().filter(e -> e.getOrderItemId().equals(i.getArgument(0))).findFirst());
        lenient().when(escrows.findByPaymentIntentId(any())).thenAnswer(i -> escrowStore.stream().filter(e -> e.getPaymentIntentId().equals(i.getArgument(0))).toList());
        lenient().when(ledgerRepo.save(any())).thenAnswer(i -> { ledgerStore.add(i.getArgument(0)); return i.getArgument(0); });
        lenient().when(payoutAccounts.findByOrganizationId(any())).thenReturn(Optional.empty());
        // no default rate for CAR on purpose
        lenient().when(rates.findFirstByVerticalAndSupplierOrganizationIdAndValidFromLessThanEqualOrderByValidFromDescCreatedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(rates.findFirstByVerticalAndSupplierOrganizationIdIsNullAndValidFromLessThanEqualOrderByValidFromDescCreatedAtDesc(any(), any()))
                .thenAnswer(i -> i.getArgument(0).toString().equals("HOTEL") ? Optional.of(rate("0.1000")) : Optional.empty());
        ledger = new LedgerService(ledgerRepo);
        intentService = new IntentServiceFactory().create();
        escrowService = new EscrowService(escrows, intents, payoutAccounts, ledger, provider);
    }

    class IntentServiceFactory {
        PaymentIntentService create() {
            return new PaymentIntentService(intents, escrows, payoutAccounts, new CommissionService(rates), ledger, orderClient, provider);
        }
    }

    static CommissionRate rate(String r) {
        CommissionRate c = new CommissionRate();
        c.setRate(new BigDecimal(r));
        return c;
    }

    OrderClient.OrderDto order(String vertical, String... amounts) {
        List<OrderClient.OrderItemDto> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        UUID[] ids = {itemId, item2Id};
        for (int i = 0; i < amounts.length; i++) {
            items.add(new OrderClient.OrderItemDto(ids[i], vertical, supplier, new BigDecimal(amounts[i]), "TRY"));
            total = total.add(new BigDecimal(amounts[i]));
        }
        return new OrderClient.OrderDto(orderId, customer, "PENDING", "TRY", total, items);
    }

    void givenOrder(OrderClient.OrderDto o) {
        lenient().when(orderClient.getOrder(orderId)).thenReturn(new OrderClient.Envelope<>(true, o, null));
        lenient().when(orderClient.markPaid(orderId)).thenReturn(new OrderClient.Envelope<>(true, o, null));
    }

    IntentResponse paid(OrderClient.OrderDto o) {
        givenOrder(o);
        IntentResponse intent = intentService.createIntent(customer, "CUSTOMER", orderId);
        return intentService.confirm(customer, "CUSTOMER", intent.id(), "TEST_OK");
    }

    void assertLedgerBalanced() {
        BigDecimal sum = ledgerStore.stream().map(e -> e.getDebitAmount().subtract(e.getCreditAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("0");
        ledgerStore.stream().collect(java.util.stream.Collectors.groupingBy(LedgerEntry::getGroupId)).values().forEach(group -> {
            BigDecimal g = group.stream().map(e -> e.getDebitAmount().subtract(e.getCreditAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(g).isEqualByComparingTo("0");
        });
    }

    @Test
    void intent_amountComesFromOrder_andIsIdempotent() {
        givenOrder(order("HOTEL", "1000.00"));
        IntentResponse first = intentService.createIntent(customer, "CUSTOMER", orderId);
        assertThat(first.amount()).isEqualByComparingTo("1000.00");
        assertThat(first.provider()).isEqualTo("TEST");
        assertThat(first.testMode()).isTrue();
        assertThat(first.status()).isEqualTo(PaymentIntentStatus.REQUIRES_PAYMENT);
        IntentResponse second = intentService.createIntent(customer, "CUSTOMER", orderId);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(intentStore).hasSize(1);
        verify(orderClient, times(1)).getOrder(orderId);
    }

    @Test
    void intent_adminForbidden_andNotOwnerForbidden() {
        givenOrder(order("HOTEL", "100.00"));
        assertThatThrownBy(() -> intentService.createIntent(customer, "ADMIN", orderId))
                .isInstanceOfSatisfying(AccessDeniedException.class, e -> assertThat(e.getCode()).isEqualTo("ADMIN_CANNOT_PURCHASE"));
        assertThatThrownBy(() -> intentService.createIntent(UUID.randomUUID(), "CUSTOMER", orderId)).isInstanceOf(AccessDeniedException.class);
        assertThat(intentStore).isEmpty();
    }

    @Test
    void intent_missingCommissionRate_is422_andNothingCreated() {
        givenOrder(order("CAR", "100.00"));
        assertThatThrownBy(() -> intentService.createIntent(customer, "CUSTOMER", orderId))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getRuleCode()).isEqualTo("COMMISSION_RATE_MISSING"));
        assertThat(intentStore).isEmpty();
    }

    @Test
    void confirm_ok_createsEscrowSnapshotLedgerAndMarksOrderPaid() {
        IntentResponse r = paid(order("HOTEL", "1000.00"));
        assertThat(r.status()).isEqualTo(PaymentIntentStatus.AUTHORIZED);
        assertThat(escrowStore).hasSize(1);
        EscrowItem e = escrowStore.get(0);
        assertThat(e.getCommissionRate()).isEqualByComparingTo("0.1000");
        assertThat(e.getCommissionAmount()).isEqualByComparingTo("100.00");
        assertThat(e.getSupplierAmount()).isEqualByComparingTo("900.00");
        assertThat(e.getStatus()).isEqualTo(EscrowStatus.HELD);
        assertThat(e.getReleaseTrigger().name()).isEqualTo("REDEMPTION");
        verify(orderClient).markPaid(orderId);
        assertLedgerBalanced();
        assertThat(ledgerStore).hasSize(2);
    }

    @Test
    void confirm_decline_failsIntent_nothingHeld_orderUntouched() {
        givenOrder(order("HOTEL", "100.00"));
        IntentResponse intent = intentService.createIntent(customer, "CUSTOMER", orderId);
        IntentResponse r = intentService.confirm(customer, "CUSTOMER", intent.id(), "TEST_DECLINE");
        assertThat(r.status()).isEqualTo(PaymentIntentStatus.FAILED);
        assertThat(r.failureCode()).isEqualTo("CARD_DECLINED");
        assertThat(escrowStore).isEmpty();
        assertThat(ledgerStore).isEmpty();
        verify(orderClient, never()).markPaid(any());
        // retry with a good token works
        assertThat(intentService.confirm(customer, "CUSTOMER", intent.id(), "TEST_OK").status()).isEqualTo(PaymentIntentStatus.AUTHORIZED);
    }

    @Test
    void confirm_isIdempotentOnceCaptured() {
        IntentResponse r = paid(order("HOTEL", "100.00"));
        IntentResponse again = intentService.confirm(customer, "CUSTOMER", r.id(), "TEST_OK");
        assertThat(again.status()).isEqualTo(PaymentIntentStatus.AUTHORIZED);
        assertThat(escrowStore).hasSize(1);
        verify(orderClient, times(1)).markPaid(orderId);
    }

    @Test
    void confirm_orderPaidCallFails_cancelsProviderPayment_andPropagates() {
        OrderClient.OrderDto o = order("HOTEL", "100.00");
        givenOrder(o);
        when(orderClient.markPaid(orderId)).thenThrow(new BusinessRuleException("HOLD_EXPIRED", "hold expired"));
        IntentResponse intent = intentService.createIntent(customer, "CUSTOMER", orderId);
        assertThatThrownBy(() -> intentService.confirm(customer, "CUSTOMER", intent.id(), "TEST_OK")).isInstanceOf(BusinessRuleException.class);
        verify(provider).cancel(any());
    }

    @Test
    void confirm_otherCustomerForbidden() {
        givenOrder(order("HOTEL", "100.00"));
        IntentResponse intent = intentService.createIntent(customer, "CUSTOMER", orderId);
        assertThatThrownBy(() -> intentService.confirm(UUID.randomUUID(), "CUSTOMER", intent.id(), "TEST_OK")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> intentService.confirm(customer, "ADMIN", intent.id(), "TEST_OK")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void release_movesFundsToSupplierPayableAndCommission_andIsIdempotent() {
        paid(order("HOTEL", "1000.00"));
        escrowService.release(itemId);
        EscrowItem e = escrowStore.get(0);
        assertThat(e.getStatus()).isEqualTo(EscrowStatus.RELEASED);
        assertThat(e.getReleasedAt()).isNotNull();
        assertThat(intentStore.get(0).getStatus()).isEqualTo(PaymentIntentStatus.RELEASED);
        assertThat(ledgerStore).anyMatch(l -> l.getAccount().name().equals("SUPPLIER_PAYABLE") && l.getCreditAmount().compareTo(new BigDecimal("900.00")) == 0);
        assertThat(ledgerStore).anyMatch(l -> l.getAccount().name().equals("PLATFORM_COMMISSION") && l.getCreditAmount().compareTo(new BigDecimal("100.00")) == 0);
        int legs = ledgerStore.size();
        escrowService.release(itemId);
        assertThat(ledgerStore).hasSize(legs);
        assertLedgerBalanced();
    }

    @Test
    void releasedItemCannotBeRefunded_andRefundedCannotBeReleased() {
        paid(order("HOTEL", "100.00"));
        escrowService.release(itemId);
        assertThatThrownBy(() -> escrowService.refund(itemId))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getRuleCode()).isEqualTo("ESCROW_ALREADY_RELEASED"));

        // fresh order for the opposite direction
        escrowStore.clear(); intentStore.clear(); ledgerStore.clear();
        paid(order("HOTEL", "100.00"));
        escrowService.refund(itemId);
        assertThatThrownBy(() -> escrowService.release(itemId))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getRuleCode()).isEqualTo("ESCROW_ALREADY_REFUNDED"));
    }

    @Test
    void refund_ledgerCustomerRefund_intentRefunded_idempotent() {
        paid(order("HOTEL", "250.00"));
        escrowService.refund(itemId);
        assertThat(escrowStore.get(0).getStatus()).isEqualTo(EscrowStatus.REFUNDED);
        assertThat(intentStore.get(0).getStatus()).isEqualTo(PaymentIntentStatus.REFUNDED);
        assertThat(ledgerStore).anyMatch(l -> l.getAccount().name().equals("CUSTOMER_REFUND") && l.getCreditAmount().compareTo(new BigDecimal("250.00")) == 0);
        int legs = ledgerStore.size();
        escrowService.refund(itemId);
        assertThat(ledgerStore).hasSize(legs);
        verify(provider, times(1)).refundItem(any());
        assertLedgerBalanced();
    }

    @Test
    void multiItem_partialStatuses_andLedgerBalances() {
        paid(order("HOTEL", "100.00", "200.00"));
        escrowService.release(itemId);
        assertThat(intentStore.get(0).getStatus()).isEqualTo(PaymentIntentStatus.PARTIALLY_RELEASED);
        escrowService.refund(item2Id);
        assertThat(intentStore.get(0).getStatus()).isEqualTo(PaymentIntentStatus.PARTIALLY_REFUNDED);
        assertLedgerBalanced();
        assertThat(ledgerStore.stream().map(LedgerEntry::getOrderId).distinct()).containsExactly(orderId);
    }

    OrderClient.OrderDto discounted(String gross, String discount, String fundedBy) {
        BigDecimal net = new BigDecimal(gross).subtract(new BigDecimal(discount));
        return new OrderClient.OrderDto(orderId, customer, "PENDING", "TRY", net, List.of(new OrderClient.OrderItemDto(
                itemId, "HOTEL", supplier, net, "TRY", new BigDecimal(gross), new BigDecimal(discount), fundedBy)));
    }

    BigDecimal sumOf(String account, boolean credit) {
        return ledgerStore.stream().filter(l -> l.getAccount().name().equals(account))
                .map(l -> credit ? l.getCreditAmount() : l.getDebitAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void platformFundedDiscount_commissionOnGross_partnerUnchanged_platformBearsDiscount() {
        paid(discounted("1000.00", "100.00", "PLATFORM"));
        EscrowItem e = escrowStore.get(0);
        assertThat(e.getGrossAmount()).isEqualByComparingTo("1000.00");
        assertThat(e.getChargedAmount()).isEqualByComparingTo("900.00");
        assertThat(e.getCommissionAmount()).isEqualByComparingTo("100.00");
        assertThat(e.getSupplierAmount()).isEqualByComparingTo("900.00");
        assertThat(sumOf("CUSTOMER_PAYMENT", false)).isEqualByComparingTo("900.00");
        escrowService.release(itemId);
        assertThat(sumOf("SUPPLIER_PAYABLE", true)).isEqualByComparingTo("900.00");
        assertThat(sumOf("PLATFORM_COMMISSION", true)).isEqualByComparingTo("100.00");
        assertThat(sumOf("PLATFORM_DISCOUNT_COST", false)).isEqualByComparingTo("100.00");
        assertLedgerBalanced();
    }

    @Test
    void partnerFundedDiscount_partnerGetsNetMinusCommission_noPlatformCost() {
        paid(discounted("1000.00", "100.00", "PARTNER"));
        EscrowItem e = escrowStore.get(0);
        assertThat(e.getCommissionAmount()).isEqualByComparingTo("100.00");
        assertThat(e.getSupplierAmount()).isEqualByComparingTo("800.00");
        escrowService.release(itemId);
        assertThat(sumOf("SUPPLIER_PAYABLE", true)).isEqualByComparingTo("800.00");
        assertThat(sumOf("PLATFORM_COMMISSION", true)).isEqualByComparingTo("100.00");
        assertThat(sumOf("PLATFORM_DISCOUNT_COST", false)).isEqualByComparingTo("0");
        assertLedgerBalanced();
    }

    @Test
    void partnerFundedDiscountLargerThanPartnerShare_422() {
        givenOrder(discounted("1000.00", "950.00", "PARTNER"));
        assertThatThrownBy(() -> intentService.createIntent(customer, "CUSTOMER", orderId))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getRuleCode()).isEqualTo("DISCOUNT_EXCEEDS_PARTNER_SHARE"));
    }

    @Test
    void discountedRefund_refundsWhatCustomerPaid_andBalances() {
        paid(discounted("1000.00", "100.00", "PLATFORM"));
        escrowService.refund(itemId);
        assertThat(sumOf("CUSTOMER_REFUND", true)).isEqualByComparingTo("900.00");
        assertThat(sumOf("PLATFORM_DISCOUNT_COST", false)).isEqualByComparingTo("0");
        assertLedgerBalanced();
    }

    @Test
    void discountedRounding_halfEven_stillBalances() {
        // 333.35 * 0.1 = 33.335 -> HALF_EVEN 33.34 (even), supplier = gross - commission
        paid(discounted("333.35", "33.33", "PLATFORM"));
        EscrowItem e = escrowStore.get(0);
        assertThat(e.getCommissionAmount()).isEqualByComparingTo("33.34");
        assertThat(e.getSupplierAmount()).isEqualByComparingTo("300.01");
        escrowService.release(itemId);
        assertLedgerBalanced();
    }

    @Test
    void inconsistentPricing_rejected() {
        OrderClient.OrderDto bad = new OrderClient.OrderDto(orderId, customer, "PENDING", "TRY", new BigDecimal("900.00"),
                List.of(new OrderClient.OrderItemDto(itemId, "HOTEL", supplier, new BigDecimal("900.00"), "TRY",
                        new BigDecimal("1000.00"), new BigDecimal("50.00"), "PLATFORM")));
        givenOrder(bad);
        assertThatThrownBy(() -> intentService.createIntent(customer, "CUSTOMER", orderId))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getRuleCode()).isEqualTo("ORDER_ITEM_PRICING_MISMATCH"));
    }

    @Test
    void deriveStatus_table() {
        assertThat(EscrowService.deriveStatus(List.of(EscrowStatus.HELD))).isEqualTo(PaymentIntentStatus.AUTHORIZED);
        assertThat(EscrowService.deriveStatus(List.of(EscrowStatus.RELEASED, EscrowStatus.HELD))).isEqualTo(PaymentIntentStatus.PARTIALLY_RELEASED);
        assertThat(EscrowService.deriveStatus(List.of(EscrowStatus.RELEASED, EscrowStatus.RELEASED))).isEqualTo(PaymentIntentStatus.RELEASED);
        assertThat(EscrowService.deriveStatus(List.of(EscrowStatus.REFUNDED, EscrowStatus.REFUNDED))).isEqualTo(PaymentIntentStatus.REFUNDED);
        assertThat(EscrowService.deriveStatus(List.of(EscrowStatus.REFUNDED, EscrowStatus.HELD))).isEqualTo(PaymentIntentStatus.PARTIALLY_REFUNDED);
    }

    // --- cancellation policy (CANCELLATION-CONTRACT.md) ---

    static com.waylo.paymentservice.dto.CancellationDtos.PolicyRequest pol(String p, Integer h, java.time.Instant start) {
        return new com.waylo.paymentservice.dto.CancellationDtos.PolicyRequest(com.waylo.paymentservice.domain.CancellationPolicy.valueOf(p), h, start);
    }

    BigDecimal net(String account) {
        return ledgerStore.stream().filter(l -> l.getAccount().name().equals(account))
                .map(l -> l.getCreditAmount().subtract(l.getDebitAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void cancel_freeCancellation_refundsChargedAmount_ledgerBalanced_idempotent() {
        paid(order("HOTEL", "1000.00"));
        var preview = escrowService.previewCancel(itemId, pol("FREE_CANCELLATION", null, null));
        var r = escrowService.cancel(itemId, pol("FREE_CANCELLATION", null, null));
        assertThat(r.outcome()).isEqualTo("REFUND");
        assertThat(r.refundAmount()).isEqualByComparingTo(preview.refundAmount()).isEqualByComparingTo("1000.00");
        assertThat(escrowStore.get(0).getStatus()).isEqualTo(EscrowStatus.REFUNDED);
        assertThat(net("CUSTOMER_REFUND")).isEqualByComparingTo("1000.00");
        assertThat(net("SUPPLIER_PAYABLE")).isEqualByComparingTo("0");
        int legs = ledgerStore.size();
        var again = escrowService.cancel(itemId, pol("NON_REFUNDABLE", null, null));
        assertThat(again.outcome()).isEqualTo("REFUND");
        assertThat(ledgerStore).hasSize(legs);
        assertLedgerBalanced();
    }

    @Test
    void cancel_nonRefundable_releasesToPartnerMinusCommission_noRefund() {
        paid(order("HOTEL", "1000.00"));
        var preview = escrowService.previewCancel(itemId, pol("NON_REFUNDABLE", null, null));
        assertThat(preview.refundAmount()).isEqualByComparingTo("0");
        var r = escrowService.cancel(itemId, pol("NON_REFUNDABLE", null, null));
        assertThat(r.outcome()).isEqualTo("NO_REFUND");
        assertThat(r.refundAmount()).isEqualByComparingTo("0");
        assertThat(r.supplierAmount()).isEqualByComparingTo("900.00");
        EscrowItem e = escrowStore.get(0);
        assertThat(e.getStatus()).isEqualTo(EscrowStatus.RELEASED);
        assertThat(e.getSettlementReason()).isEqualTo("CANCEL_NO_REFUND");
        assertThat(net("SUPPLIER_PAYABLE")).isEqualByComparingTo("900.00");
        assertThat(net("PLATFORM_COMMISSION")).isEqualByComparingTo("100.00");
        assertThat(net("CUSTOMER_REFUND")).isEqualByComparingTo("0");
        // repeat is idempotent and still cancellable-as-done
        int legs = ledgerStore.size();
        assertThat(escrowService.cancel(itemId, pol("NON_REFUNDABLE", null, null)).outcome()).isEqualTo("NO_REFUND");
        assertThat(ledgerStore).hasSize(legs);
        assertLedgerBalanced();
    }

    @Test
    void cancel_afterRedemption_isRejected_andPreviewSaysNotCancellable() {
        paid(order("HOTEL", "100.00"));
        escrowService.release(itemId);
        assertThat(escrowService.previewCancel(itemId, pol("FREE_CANCELLATION", null, null)).cancellable()).isFalse();
        assertThatThrownBy(() -> escrowService.cancel(itemId, pol("FREE_CANCELLATION", null, null)))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getRuleCode()).isEqualTo("ESCROW_ALREADY_RELEASED"));
    }

    @Test
    void cancel_freeUntil_dependsOnDeadline() {
        java.time.Instant start = java.time.Instant.now().plus(java.time.Duration.ofHours(30));
        paid(order("HOTEL", "100.00"));
        assertThat(escrowService.previewCancel(itemId, pol("FREE_UNTIL", 24, start)).outcome()).isEqualTo("REFUND");
        assertThat(escrowService.previewCancel(itemId, pol("FREE_UNTIL", 48, start)).outcome()).isEqualTo("NO_REFUND");
    }

    @Test
    void noShow_releasesToPartner_forFreeCancellationToo_andIsIdempotent() {
        paid(order("HOTEL", "1000.00"));
        var r = escrowService.noShow(itemId);
        assertThat(r.outcome()).isEqualTo("NO_REFUND");
        assertThat(escrowStore.get(0).getStatus()).isEqualTo(EscrowStatus.RELEASED);
        assertThat(escrowStore.get(0).getSettlementReason()).isEqualTo("NO_SHOW");
        assertThat(net("SUPPLIER_PAYABLE")).isEqualByComparingTo("900.00");
        int legs = ledgerStore.size();
        escrowService.noShow(itemId);
        assertThat(ledgerStore).hasSize(legs);
        assertLedgerBalanced();
    }

    @Test
    void noShow_onRefundedItem_changesNothing() {
        paid(order("HOTEL", "100.00"));
        escrowService.refund(itemId);
        int legs = ledgerStore.size();
        assertThat(escrowService.noShow(itemId).outcome()).isEqualTo("REFUND");
        assertThat(ledgerStore).hasSize(legs);
    }

    @Test
    void discountedItem_partnerFunded_refundIsChargedNet_andProportionalLedgerReverses() {
        // supplier price 1000, partner funded discount 200 -> customer paid 800, commission 10% of net (80), supplier 720
        OrderClient.OrderDto o = new OrderClient.OrderDto(orderId, customer, "PENDING", "TRY", new BigDecimal("800.00"),
                List.of(new OrderClient.OrderItemDto(itemId, "HOTEL", supplier, new BigDecimal("800.00"), "TRY",
                        new BigDecimal("1000.00"), new BigDecimal("200.00"), "PARTNER")));
        paid(o);
        EscrowItem e = escrowStore.get(0);
        assertThat(e.getChargedAmount()).isEqualByComparingTo("800.00");
        var r = escrowService.cancel(itemId, pol("FREE_CANCELLATION", null, null));
        assertThat(r.refundAmount()).isEqualByComparingTo("800.00");
        assertThat(net("CUSTOMER_REFUND")).isEqualByComparingTo("800.00");
        assertLedgerBalanced();
    }

    @Test
    void discountedItem_platformFunded_nonRefundable_releaseUsesDiscountCostAndBalances() {
        OrderClient.OrderDto o = new OrderClient.OrderDto(orderId, customer, "PENDING", "TRY", new BigDecimal("800.00"),
                List.of(new OrderClient.OrderItemDto(itemId, "HOTEL", supplier, new BigDecimal("800.00"), "TRY",
                        new BigDecimal("1000.00"), new BigDecimal("200.00"), "PLATFORM")));
        paid(o);
        var r = escrowService.cancel(itemId, pol("NON_REFUNDABLE", null, null));
        assertThat(r.refundAmount()).isEqualByComparingTo("0");
        assertThat(net("SUPPLIER_PAYABLE")).isEqualByComparingTo("900.00");
        assertLedgerBalanced();
    }
}
