package com.waylo.paymentservice.service;

import com.waylo.paymentservice.domain.CommissionRate;
import com.waylo.paymentservice.domain.Vertical;
import com.waylo.paymentservice.exception.BusinessRuleException;
import com.waylo.paymentservice.exception.CommissionRateConflictException;
import com.waylo.paymentservice.repository.CommissionRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionServiceTest {

    @Mock CommissionRateRepository repository;

    private final UUID org = UUID.randomUUID();

    private static CommissionRate rate(String r) {
        CommissionRate c = new CommissionRate();
        c.setRate(new BigDecimal(r));
        return c;
    }

    @Test
    void split_roundsHalfEvenAndAlwaysAddsUp() {
        // 100.05 * 0.1000 = 10.005 -> HALF_EVEN -> 10.00 (0 is even), not 10.01
        var a = CommissionService.split(new BigDecimal("100.05"), new BigDecimal("0.1000"));
        assertThat(a.commission()).isEqualByComparingTo("10.00");
        assertThat(a.supplier()).isEqualByComparingTo("90.05");
        // 100.15 * 0.1000 = 10.015 -> 10.02 (1 is odd, rounds up to even)
        var b = CommissionService.split(new BigDecimal("100.15"), new BigDecimal("0.1000"));
        assertThat(b.commission()).isEqualByComparingTo("10.02");
        assertThat(b.commission().add(b.supplier())).isEqualByComparingTo("100.15");
        assertThat(b.commission().scale()).isEqualTo(2);
    }

    @Test
    void split_zeroAndFullRate() {
        var zero = CommissionService.split(new BigDecimal("50.00"), new BigDecimal("0.0000"));
        assertThat(zero.commission()).isEqualByComparingTo("0.00");
        assertThat(zero.supplier()).isEqualByComparingTo("50.00");
        var full = CommissionService.split(new BigDecimal("50.00"), BigDecimal.ONE);
        assertThat(full.supplier()).isEqualByComparingTo("0.00");
    }

    @Test
    void resolve_overrideWinsOverVerticalDefault() {
        lenient().when(repository.findFirstByVerticalAndSupplierOrganizationIdAndValidFromLessThanEqualOrderByValidFromDescCreatedAtDesc(
                eq(Vertical.HOTEL), eq(org), any())).thenReturn(Optional.of(rate("0.0500")));
        lenient().when(repository.findFirstByVerticalAndSupplierOrganizationIdIsNullAndValidFromLessThanEqualOrderByValidFromDescCreatedAtDesc(
                eq(Vertical.HOTEL), any())).thenReturn(Optional.of(rate("0.1500")));
        assertThat(new CommissionService(repository).resolveRate(Vertical.HOTEL, org, Instant.now())).isEqualByComparingTo("0.05");
    }

    @Test
    void resolve_fallsBackToVerticalDefault() {
        lenient().when(repository.findFirstByVerticalAndSupplierOrganizationIdAndValidFromLessThanEqualOrderByValidFromDescCreatedAtDesc(
                eq(Vertical.HOTEL), eq(org), any())).thenReturn(Optional.empty());
        lenient().when(repository.findFirstByVerticalAndSupplierOrganizationIdIsNullAndValidFromLessThanEqualOrderByValidFromDescCreatedAtDesc(
                eq(Vertical.HOTEL), any())).thenReturn(Optional.of(rate("0.1500")));
        assertThat(new CommissionService(repository).resolveRate(Vertical.HOTEL, org, Instant.now())).isEqualByComparingTo("0.15");
    }

    @Test
    void resolve_missingRate_is422CommissionRateMissing() {
        lenient().when(repository.findFirstByVerticalAndSupplierOrganizationIdAndValidFromLessThanEqualOrderByValidFromDescCreatedAtDesc(
                any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(repository.findFirstByVerticalAndSupplierOrganizationIdIsNullAndValidFromLessThanEqualOrderByValidFromDescCreatedAtDesc(
                any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new CommissionService(repository).resolveRate(Vertical.CAR, org, Instant.now()))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getRuleCode()).isEqualTo("COMMISSION_RATE_MISSING"));
    }

    @Test
    void create_rejectsOutOfRangeAndTooManyDecimals() {
        CommissionService s = new CommissionService(repository);
        assertThatThrownBy(() -> s.create(Vertical.BUS, null, new BigDecimal("1.0001"), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.create(Vertical.BUS, null, new BigDecimal("-0.01"), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.create(Vertical.BUS, null, new BigDecimal("0.12345"), null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_conflict_wrapsDataIntegrityViolation() {
        CommissionService s = new CommissionService(repository);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        assertThatThrownBy(() -> s.create(Vertical.HOTEL, org, new BigDecimal("0.1500"), Instant.now(), null))
                .isInstanceOfSatisfying(CommissionRateConflictException.class,
                        e -> assertThat(e.getMessage()).contains("already exists"));
    }
}
