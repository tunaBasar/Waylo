package com.waylo.paymentservice.service;

import com.waylo.paymentservice.domain.CommissionRate;
import com.waylo.paymentservice.domain.Vertical;
import com.waylo.paymentservice.exception.BusinessRuleException;
import com.waylo.paymentservice.repository.CommissionRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Commission lookup and math. No default rates exist: a vertical without a configured rate cannot be sold
 * (422 COMMISSION_RATE_MISSING). Precedence: supplier override (latest validFrom &lt;= now), then vertical default.
 */
@Service
public class CommissionService {

    public static final String RATE_MISSING = "COMMISSION_RATE_MISSING";

    private final CommissionRateRepository repository;

    public CommissionService(CommissionRateRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public BigDecimal resolveRate(Vertical vertical, UUID supplierOrganizationId, Instant at) {
        if (supplierOrganizationId != null) {
            var override = repository
                    .findFirstByVerticalAndSupplierOrganizationIdAndValidFromLessThanEqualOrderByValidFromDescCreatedAtDesc(
                            vertical, supplierOrganizationId, at);
            if (override.isPresent()) return override.get().getRate();
        }
        return repository
                .findFirstByVerticalAndSupplierOrganizationIdIsNullAndValidFromLessThanEqualOrderByValidFromDescCreatedAtDesc(
                        vertical, at)
                .map(CommissionRate::getRate)
                .orElseThrow(() -> new BusinessRuleException(RATE_MISSING,
                        "No commission rate is configured for vertical " + vertical + ", sales are closed for it"));
    }

    /** commission = gross x rate rounded HALF_EVEN to 2 decimals, supplier = gross - commission (so the two always add up). */
    public static Split split(BigDecimal gross, BigDecimal rate) {
        BigDecimal commission = gross.multiply(rate).setScale(2, RoundingMode.HALF_EVEN);
        return new Split(commission, gross.subtract(commission));
    }

    public record Split(BigDecimal commission, BigDecimal supplier) {}

    @Transactional(readOnly = true)
    public List<CommissionRate> list() {
        return repository.findAllByOrderByVerticalAscValidFromDesc();
    }

    /** Creates a new version. Existing rows are never edited, so snapshots and history stay explainable. */
    @Transactional
    public CommissionRate create(Vertical vertical, UUID supplierOrganizationId, BigDecimal rate, Instant validFrom, UUID adminId) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("rate must be between 0 and 1");
        }
        if (rate.stripTrailingZeros().scale() > 4) {
            throw new IllegalArgumentException("rate allows at most 4 decimal places");
        }
        CommissionRate row = new CommissionRate();
        row.setId(UUID.randomUUID());
        row.setVertical(vertical);
        row.setSupplierOrganizationId(supplierOrganizationId);
        row.setRate(rate.setScale(4, RoundingMode.UNNECESSARY));
        row.setValidFrom(validFrom != null ? validFrom : Instant.now());
        row.setCreatedBy(adminId);
        return repository.save(row);
    }
}
