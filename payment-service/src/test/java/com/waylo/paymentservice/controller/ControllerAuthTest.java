package com.waylo.paymentservice.controller;

import com.waylo.paymentservice.dto.CommissionRateRequest;
import com.waylo.paymentservice.exception.AccessDeniedException;
import com.waylo.paymentservice.exception.AuthenticationRequiredException;
import com.waylo.paymentservice.domain.Vertical;
import com.waylo.paymentservice.service.CommissionService;
import com.waylo.paymentservice.service.LedgerService;
import com.waylo.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ControllerAuthTest {

    CommissionService commission = mock(CommissionService.class);
    LedgerService ledger = mock(LedgerService.class);
    PaymentService legacy = mock(PaymentService.class);
    AdminFinanceController admin = new AdminFinanceController(commission, ledger);
    LegacyPaymentController legacyController = new LegacyPaymentController(legacy);
    UUID user = UUID.randomUUID();

    @Test
    void adminEndpoints_rejectNonAdminAndAnonymous() {
        var req = new CommissionRateRequest(Vertical.HOTEL, null, new BigDecimal("0.1"), null);
        assertThatThrownBy(() -> admin.createRate(user, "CUSTOMER", req)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> admin.listRates(user, "PARTNER")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> admin.ledger(user, "CUSTOMER", UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> admin.listRates(null, null)).isInstanceOf(AuthenticationRequiredException.class);
        verifyNoInteractions(commission, ledger);
    }

    @Test
    void payoutAdminEndpoints_rejectNonAdminAndAnonymous_beforeTouchingAnyRepository() {
        var access = mock(com.waylo.paymentservice.service.PayoutAccess.class);
        var payoutRepo = mock(com.waylo.paymentservice.repository.PartnerPayoutRepository.class);
        var accountRepo = mock(com.waylo.paymentservice.repository.PartnerBankAccountRepository.class);
        var ledgerRepo = mock(com.waylo.paymentservice.repository.PayoutLedgerRepository.class);
        var payoutService = new com.waylo.paymentservice.service.PayoutService(access, payoutRepo, ledgerRepo,
                mock(com.waylo.paymentservice.repository.PayoutEventRepository.class), accountRepo,
                mock(com.waylo.paymentservice.repository.PayoutWalletRepository.class),
                mock(com.waylo.paymentservice.service.PayoutBalanceService.class), new com.waylo.paymentservice.config.PayoutSettings(),
                mock(com.waylo.paymentservice.event.PaymentEventPublisher.class));
        var bankService = new com.waylo.paymentservice.service.BankAccountService(access, accountRepo,
                mock(com.waylo.paymentservice.repository.BankAccountChangeRepository.class), payoutRepo,
                new com.waylo.paymentservice.service.IbanCrypto(java.util.Base64.getEncoder().encodeToString(new byte[32])));
        var controller = new AdminPayoutController(payoutService, bankService);
        UUID id = UUID.randomUUID();
        var paid = new com.waylo.paymentservice.dto.PayoutDtos.PaidRequest("REF-1234", null, null);
        var reason = new com.waylo.paymentservice.dto.PayoutDtos.ReasonRequest("some reason", null);

        for (String role : new String[]{"CUSTOMER", "PARTNER", "HOTEL_STAFF", null}) {
            assertThatThrownBy(() -> controller.queue(user, role, null, null, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.detail(user, role, id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.bankDetails(user, role, id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.approve(user, role, id, null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.processing(user, role, id, null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.paid(user, role, id, paid)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.failed(user, role, id, reason)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.cancel(user, role, id, reason)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.bankAccountQueue(user, role, null, 10)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.verify(user, role, id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.reject(user, role, id, new com.waylo.paymentservice.dto.PayoutDtos.RejectBankAccountRequest("some reason"))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.payoutBalance(user, role, id)).isInstanceOf(AccessDeniedException.class);
        }
        assertThatThrownBy(() -> controller.approve(null, "ADMIN", id, null)).isInstanceOf(AuthenticationRequiredException.class);
        assertThatThrownBy(() -> controller.queue(null, null, null, null, null, 0, 20)).isInstanceOf(AuthenticationRequiredException.class);
        assertThatThrownBy(() -> controller.bankDetails(null, "ADMIN", id)).isInstanceOf(AuthenticationRequiredException.class);
        verifyNoInteractions(payoutRepo, accountRepo, ledgerRepo);
    }

    @Test
    void legacyMockHistory_isAdminOnly() {
        assertThatThrownBy(() -> legacyController.getAll(user, "CUSTOMER")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> legacyController.getById(user, "CUSTOMER", UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(legacy);
    }
}
