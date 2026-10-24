package com.finsecure.pay.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.finsecure.pay.config.ValidationProperties;
import com.finsecure.pay.model.TransactionRequest;
import com.finsecure.pay.model.ValidationResult;

/**
 * Applies the FinSecure Pay validation rules to a transaction.
 * Every failed rule adds a reason; no reasons means APPROVED.
 */
@Service
public class TransactionValidator {

    private static final Logger log = LoggerFactory.getLogger(TransactionValidator.class);

    private final ValidationProperties properties;

    // Approved transaction IDs, used to reject replays. In-memory per pod;
    // a shared store (e.g. Redis) would be needed across replicas.
    private final Set<String> approvedIds = ConcurrentHashMap.newKeySet();

    public TransactionValidator(ValidationProperties properties) {
        this.properties = properties;
    }

    public ValidationResult validate(TransactionRequest request) {
        List<String> reasons = new ArrayList<>();

        String txnId = request.transactionId();
        if (isBlank(txnId)) {
            reasons.add("transactionId is required");
        } else if (approvedIds.contains(txnId)) {
            reasons.add("duplicate transactionId");
        }

        BigDecimal amount = request.amount();
        if (amount == null || amount.signum() <= 0) {
            reasons.add("amount must be greater than 0");
        } else if (amount.compareTo(properties.maxAmount()) > 0) {
            reasons.add("amount exceeds limit of " + properties.maxAmount().toPlainString());
        }

        String currency = request.currency();
        if (currency == null || !properties.allowedCurrencies().contains(currency.toUpperCase())) {
            reasons.add("currency must be one of " + properties.allowedCurrencies());
        }

        if (!isValidCardNumber(request.cardNumber())) {
            reasons.add("cardNumber must be 16 digits and pass the Luhn check");
        }

        if (isBlank(request.merchantId())) {
            reasons.add("merchantId is required");
        }

        ValidationResult result = ValidationResult.of(txnId, reasons);
        if (result.status() == ValidationResult.Status.APPROVED) {
            approvedIds.add(txnId);
        }

        log.info("validation txnId={} status={} card={} reasons={}",
                txnId, result.status(), maskCard(request.cardNumber()), reasons);
        return result;
    }

    static boolean isValidCardNumber(String cardNumber) {
        if (cardNumber == null || !cardNumber.matches("\\d{16}")) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = cardNumber.charAt(i) - '0';
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    // Never log a full card number.
    static String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "****" + cardNumber.substring(cardNumber.length() - 4);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
