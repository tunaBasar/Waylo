package com.waylo.paymentservice.exception;

import java.util.Map;

public class BusinessRuleException extends RuntimeException {

    private final String ruleCode;
    private final transient Map<String, Object> details;

    public BusinessRuleException(String ruleCode, String message) {
        this(ruleCode, message, null);
    }

    public BusinessRuleException(String ruleCode, String message, Map<String, Object> details) {
        super(message);
        this.ruleCode = ruleCode;
        this.details = details;
    }

    public BusinessRuleException(String message) {
        this("BUSINESS_RULE_VIOLATION", message);
    }

    public String getRuleCode() { return ruleCode; }
    public Map<String, Object> getDetails() { return details; }
}
