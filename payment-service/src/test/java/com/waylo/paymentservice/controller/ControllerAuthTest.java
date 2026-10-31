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
    void legacyMockHistory_isAdminOnly() {
        assertThatThrownBy(() -> legacyController.getAll(user, "CUSTOMER")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> legacyController.getById(user, "CUSTOMER", UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(legacy);
    }
}
