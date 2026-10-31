package com.waylo.paymentservice.dto;

import java.util.List;

public record OrderPaymentResponse(IntentResponse intent, List<CustomerEscrowItemView> items) {}
