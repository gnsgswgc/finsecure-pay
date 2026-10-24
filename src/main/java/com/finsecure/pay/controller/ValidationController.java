package com.finsecure.pay.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.finsecure.pay.model.TransactionRequest;
import com.finsecure.pay.model.ValidationResult;
import com.finsecure.pay.service.TransactionValidator;

@RestController
public class ValidationController {

    private final TransactionValidator validator;

    public ValidationController(TransactionValidator validator) {
        this.validator = validator;
    }

    // Returns 200 for both outcomes: a REJECTED transaction is a valid business result.
    @PostMapping("/validate")
    public ValidationResult validate(@RequestBody TransactionRequest request) {
        return validator.validate(request);
    }
}
