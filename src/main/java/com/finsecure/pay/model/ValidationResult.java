package com.finsecure.pay.model;

import java.util.List;

public record ValidationResult(String transactionId, Status status, List<String> reasons) {

    public enum Status { APPROVED, REJECTED }

    public static ValidationResult of(String transactionId, List<String> reasons) {
        Status status = reasons.isEmpty() ? Status.APPROVED : Status.REJECTED;
        return new ValidationResult(transactionId, status, List.copyOf(reasons));
    }
}
