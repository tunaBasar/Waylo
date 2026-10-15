package com.waylo.hotelservice.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessRuleExceptionTest {

    @Test
    void withRuleCode_and_message() {
        BusinessRuleException ex = new BusinessRuleException("INVALID_CAPACITY", "Capacity must be positive");

        assertThat(ex.getRuleCode()).isEqualTo("INVALID_CAPACITY");
        assertThat(ex.getMessage()).isEqualTo("Capacity must be positive");
    }

    @Test
    void withMessage_only_defaultsToBusinessRuleViolation() {
        BusinessRuleException ex = new BusinessRuleException("Room capacity is negative");

        assertThat(ex.getRuleCode()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(ex.getMessage()).isEqualTo("Room capacity is negative");
    }
}
