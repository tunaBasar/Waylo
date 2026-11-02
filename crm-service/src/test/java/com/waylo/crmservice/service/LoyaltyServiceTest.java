package com.waylo.crmservice.service;

import com.waylo.crmservice.domain.Customer;
import com.waylo.crmservice.domain.CustomerTier;
import com.waylo.crmservice.repository.CustomerRepository;
import com.waylo.crmservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoyaltyServiceTest {
    CustomerRepository customers = mock(CustomerRepository.class);
    ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    UUID cid = UUID.randomUUID(), item = UUID.randomUUID();
    Customer c = new Customer();

    LoyaltyService svc(String ppt, int silver, int gold, int plat) {
        when(customers.findById(cid)).thenReturn(Optional.of(c));
        return new LoyaltyService(customers, processed, new BigDecimal(ppt), silver, gold, plat);
    }

    @Test void disabledByDefault_awardsNothing() {
        LoyaltyService s = svc("0", 0, 0, 0);
        assertThat(s.isEnabled()).isFalse();
        assertThat(s.awardForRedeemedItem(item, cid, "TRY", new BigDecimal("1000"))).isZero();
        verify(customers, never()).save(any());
        verify(processed, never()).saveAndFlush(any());
    }

    @Test void enabled_awardsPoints_andTierOnlyWhenThresholdConfigured() {
        LoyaltyService s = svc("0.5", 100, 0, 0);
        assertThat(s.awardForRedeemedItem(item, cid, "TRY", new BigDecimal("300"))).isEqualTo(150);
        assertThat(c.getLoyaltyPoints()).isEqualTo(150);
        assertThat(c.getTier()).isEqualTo(CustomerTier.SILVER);
        verify(customers).save(c);
    }

    @Test void tiersDisabledByDefault_keepsBronze() {
        LoyaltyService s = svc("1", 0, 0, 0);
        s.awardForRedeemedItem(item, cid, "TRY", new BigDecimal("5000"));
        assertThat(c.getTier()).isEqualTo(CustomerTier.BRONZE);
    }

    @Test void duplicateItem_isNotAwardedTwice() {
        LoyaltyService s = svc("1", 0, 0, 0);
        when(processed.existsByEventIdAndConsumerGroup("order-item-redeemed:" + item, "crm-service")).thenReturn(false, true);
        assertThat(s.awardForRedeemedItem(item, cid, "TRY", new BigDecimal("10"))).isEqualTo(10);
        assertThat(s.awardForRedeemedItem(item, cid, "TRY", new BigDecimal("10"))).isZero();
        assertThat(c.getLoyaltyPoints()).isEqualTo(10);
    }

    @Test void nonTryCurrency_ignored() {
        LoyaltyService s = svc("1", 0, 0, 0);
        assertThat(s.awardForRedeemedItem(item, cid, "USD", new BigDecimal("10"))).isZero();
    }
}
