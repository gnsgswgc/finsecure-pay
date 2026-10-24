package com.finsecure.pay.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.finsecure.pay.config.ValidationProperties;
import com.finsecure.pay.model.TransactionRequest;
import com.finsecure.pay.model.ValidationResult.Status;

class TransactionValidatorTest {

    private static final String VALID_CARD = "4111111111111111";

    private TransactionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TransactionValidator(
                new ValidationProperties(new BigDecimal("10000"), Set.of("SGD", "USD", "EUR")));
    }

    private static TransactionRequest txn(String id, String amount, String currency, String card, String merchant) {
        return new TransactionRequest(id, amount == null ? null : new BigDecimal(amount), currency, card, merchant);
    }

    @Test
    @DisplayName("Valid transaction is approved")
    void validTransactionIsApproved() {
        var result = validator.validate(txn("T1", "250.00", "SGD", VALID_CARD, "M001"));

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    @DisplayName("Amount exactly at the limit is approved")
    void amountAtLimitIsApproved() {
        var result = validator.validate(txn("T2", "10000", "USD", VALID_CARD, "M001"));

        assertThat(result.status()).isEqualTo(Status.APPROVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "-0.01"})
    @DisplayName("Zero or negative amount is rejected")
    void nonPositiveAmountIsRejected(String amount) {
        var result = validator.validate(txn("T3", amount, "SGD", VALID_CARD, "M001"));

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        assertThat(result.reasons()).contains("amount must be greater than 0");
    }

    @Test
    @DisplayName("Amount above the limit is rejected")
    void amountOverLimitIsRejected() {
        var result = validator.validate(txn("T4", "10000.01", "SGD", VALID_CARD, "M001"));

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        assertThat(result.reasons()).anyMatch(r -> r.startsWith("amount exceeds limit"));
    }

    @Test
    @DisplayName("Missing amount is rejected")
    void missingAmountIsRejected() {
        var result = validator.validate(txn("T5", null, "SGD", VALID_CARD, "M001"));

        assertThat(result.status()).isEqualTo(Status.REJECTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"JPY", "GBP", "XYZ"})
    @DisplayName("Unsupported currency is rejected")
    void unsupportedCurrencyIsRejected(String currency) {
        var result = validator.validate(txn("T6", "100", currency, VALID_CARD, "M001"));

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        assertThat(result.reasons()).anyMatch(r -> r.startsWith("currency must be one of"));
    }

    @Test
    @DisplayName("Lower-case supported currency is accepted")
    void lowerCaseCurrencyIsAccepted() {
        var result = validator.validate(txn("T7", "100", "eur", VALID_CARD, "M001"));

        assertThat(result.status()).isEqualTo(Status.APPROVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234", "4111111111111112", "41111111111111111", "4111-1111-1111-1111", "abcdabcdabcdabcd"})
    @DisplayName("Invalid card number is rejected")
    void invalidCardIsRejected(String card) {
        var result = validator.validate(txn("T8", "100", "SGD", card, "M001"));

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        assertThat(result.reasons()).contains("cardNumber must be 16 digits and pass the Luhn check");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Missing merchant ID is rejected")
    void missingMerchantIsRejected(String merchant) {
        var result = validator.validate(txn("T9", "100", "SGD", VALID_CARD, merchant));

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        assertThat(result.reasons()).contains("merchantId is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Missing transaction ID is rejected")
    void missingTransactionIdIsRejected(String id) {
        var result = validator.validate(txn(id, "100", "SGD", VALID_CARD, "M001"));

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        assertThat(result.reasons()).contains("transactionId is required");
    }

    @Test
    @DisplayName("Replay of an approved transaction ID is rejected")
    void duplicateTransactionIsRejected() {
        validator.validate(txn("T10", "100", "SGD", VALID_CARD, "M001"));

        var replay = validator.validate(txn("T10", "100", "SGD", VALID_CARD, "M001"));

        assertThat(replay.status()).isEqualTo(Status.REJECTED);
        assertThat(replay.reasons()).contains("duplicate transactionId");
    }

    @Test
    @DisplayName("A rejected transaction ID can be retried")
    void rejectedTransactionCanBeRetried() {
        validator.validate(txn("T11", "-1", "SGD", VALID_CARD, "M001"));

        var retry = validator.validate(txn("T11", "100", "SGD", VALID_CARD, "M001"));

        assertThat(retry.status()).isEqualTo(Status.APPROVED);
    }

    @Test
    @DisplayName("All failed rules are reported together")
    void multipleFailuresAreAllReported() {
        var result = validator.validate(txn("T12", "-5", "JPY", "1234", ""));

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        assertThat(result.reasons()).hasSize(4);
    }

    @Test
    @DisplayName("Card number is masked for logging")
    void cardIsMasked() {
        assertThat(TransactionValidator.maskCard(VALID_CARD)).isEqualTo("****1111");
        assertThat(TransactionValidator.maskCard(null)).isEqualTo("****");
    }
}
