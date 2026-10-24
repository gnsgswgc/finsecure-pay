package com.finsecure.pay.config;

import java.math.BigDecimal;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Validation limits, read from application.yml and overridable by environment
 * variables (e.g. FINSECURE_VALIDATION_MAX_AMOUNT from a Kubernetes ConfigMap).
 */
@ConfigurationProperties(prefix = "finsecure.validation")
public record ValidationProperties(BigDecimal maxAmount, Set<String> allowedCurrencies) {

    public ValidationProperties {
        if (maxAmount == null) {
            maxAmount = new BigDecimal("10000");
        }
        if (allowedCurrencies == null || allowedCurrencies.isEmpty()) {
            allowedCurrencies = Set.of("SGD", "USD", "EUR");
        }
    }
}
