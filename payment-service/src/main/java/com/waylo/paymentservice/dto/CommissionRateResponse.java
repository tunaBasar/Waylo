package com.waylo.paymentservice.dto;

import com.waylo.paymentservice.domain.CommissionRate;
import com.waylo.paymentservice.domain.Vertical;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CommissionRateResponse(UUID id, Vertical vertical, UUID supplierOrganizationId, BigDecimal rate,
                                     Instant validFrom, UUID createdBy, Instant createdAt) {
    public static CommissionRateResponse from(CommissionRate r) {
        return new CommissionRateResponse(r.getId(), r.getVertical(), r.getSupplierOrganizationId(), r.getRate(),
                r.getValidFrom(), r.getCreatedBy(), r.getCreatedAt());
    }
}
