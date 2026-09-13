package com.waylo.carrentalservice.exception;

public class BusinessRuleException extends RuntimeException {

    private final String ruleCode;

    public BusinessRuleException(String ruleCode, String message) {
        super(message);
        this.ruleCode = ruleCode;
    }

    public BusinessRuleException(String message) {
        this("BUSINESS_RULE_VIOLATION", message);
    }

    public String getRuleCode() { return ruleCode; }
}
