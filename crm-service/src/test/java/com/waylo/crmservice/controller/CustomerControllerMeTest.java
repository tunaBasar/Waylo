package com.waylo.crmservice.controller;

import com.waylo.crmservice.domain.CustomerTier;
import com.waylo.crmservice.dto.CustomerResponse;
import com.waylo.crmservice.service.CustomerService;
import com.waylo.crmservice.service.LoyaltyService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CustomerControllerMeTest {
    UUID id = UUID.randomUUID();
    CustomerService service = mock(CustomerService.class);
    LoyaltyService loyalty = mock(LoyaltyService.class);

    @Test void me_returnsOwnRecordOnly_hidesLoyaltyWhenDisabled() {
        when(service.getById(id)).thenReturn(new CustomerResponse(id, "A", "B", "a@b.c", "1", CustomerTier.GOLD, 50, true, Instant.now(), Instant.now()));
        when(loyalty.isEnabled()).thenReturn(false);
        var d = new CustomerController(service, loyalty).me(id).getBody().data();
        assertThat(d.loyaltyEnabled()).isFalse();
        assertThat(d.loyaltyPoints()).isNull();
        assertThat(d.tier()).isNull();
        verify(service).getById(id);
    }

    @Test void me_showsLoyaltyWhenEnabled() {
        when(service.getById(id)).thenReturn(new CustomerResponse(id, "A", "B", "a@b.c", "1", CustomerTier.GOLD, 50, true, Instant.now(), Instant.now()));
        when(loyalty.isEnabled()).thenReturn(true);
        var d = new CustomerController(service, loyalty).me(id).getBody().data();
        assertThat(d.loyaltyPoints()).isEqualTo(50);
        assertThat(d.tier()).isEqualTo(CustomerTier.GOLD);
    }

    @Test void response_hasNoPassportNumberField() {
        assertThat(java.util.Arrays.stream(CustomerResponse.class.getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("passportNumber");
        assertThat(java.util.Arrays.stream(com.waylo.crmservice.dto.MyCustomerResponse.class.getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("passportNumber", "phone");
    }
}
