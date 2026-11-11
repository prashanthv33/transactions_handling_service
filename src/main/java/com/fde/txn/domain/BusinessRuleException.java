package com.fde.txn.domain;

/** A permanent, non-retryable failure caused by a business rule (e.g. insufficient funds). */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
