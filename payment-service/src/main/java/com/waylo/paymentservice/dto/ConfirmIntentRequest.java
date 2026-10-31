package com.waylo.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;

/** paymentToken is an opaque provider token (TEST: TEST_OK / TEST_DECLINE). Raw card data is never accepted. */
public record ConfirmIntentRequest(@NotBlank String paymentToken) {}
