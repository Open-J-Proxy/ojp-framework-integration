package com.example.atomikos.controller;

import com.example.atomikos.dto.CreateAccountRequest;
import com.example.atomikos.dto.CreateAccountWithFailureRequest;
import com.example.atomikos.dto.TransferRequest;
import com.example.atomikos.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired
    private TransactionService transactionService;

    @PostMapping("/accounts")
    public ResponseEntity<String> createAccount(@RequestBody CreateAccountRequest request) {
        try {
            transactionService.createAccountWithAudit(
                request.getAccountNumber(),
                request.getAccountHolder(),
                request.getBalance()
            );
            return ResponseEntity.ok("Account created successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to create account: " + e.getMessage());
        }
    }

    @PostMapping("/transfer")
    public ResponseEntity<String> transfer(@RequestBody TransferRequest request) {
        try {
            transactionService.transferWithAudit(
                request.getFromAccountNumber(),
                request.getToAccountNumber(),
                request.getAmount()
            );
            return ResponseEntity.ok("Transfer completed successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to transfer: " + e.getMessage());
        }
    }

    @PostMapping("/accounts/with-failure")
    public ResponseEntity<String> createAccountWithFailure(@RequestBody CreateAccountWithFailureRequest request) {
        try {
            transactionService.createAccountWithAuditAndFailure(
                request.getAccountNumber(),
                request.getAccountHolder(),
                request.getBalance(),
                request.isFailAfterAccount()
            );
            return ResponseEntity.ok("Account created successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to create account: " + e.getMessage());
        }
    }

    @PostMapping("/accounts/with-timeout")
    public ResponseEntity<String> createAccountWithTimeout(@RequestBody CreateAccountRequest request) {
        try {
            transactionService.createAccountWithTimeout(
                request.getAccountNumber(),
                request.getAccountHolder(),
                request.getBalance()
            );
            return ResponseEntity.ok("Account created successfully");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Operation interrupted: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to create account: " + e.getMessage());
        }
    }
}
