package com.waylo.packagebookingservice.dto;

import java.time.Instant;
import java.util.UUID;

public record StayOperationResponse(UUID orderItemId, String voucherStatus, Instant checkedOutAt, long version) {}
