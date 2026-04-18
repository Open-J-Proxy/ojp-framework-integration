package com.example.atomikos.integration;

import com.example.atomikos.dto.CreateAccountRequest;
import com.example.atomikos.dto.CreateAccountWithFailureRequest;
import com.example.atomikos.dto.TransferRequest;
import com.example.atomikos.entity.postgres.Account;
import com.example.atomikos.entity.postgres2.AuditLog;
import com.example.atomikos.repository.postgres.AccountRepository;
import com.example.atomikos.repository.postgres2.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class DistributedTransactionRollbackIT extends AbstractAtomikosIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setup() {
        auditLogRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void testRollbackWhenExceptionOccurs() {
        // Given
        String accountNumber = "ACC999";
        String accountHolder = "Test User";
        BigDecimal balance = new BigDecimal("500.00");
        CreateAccountWithFailureRequest request = new CreateAccountWithFailureRequest(
            accountNumber, accountHolder, balance, true);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/transactions/accounts/with-failure", request, String.class);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().contains("Simulated failure after account creation"));

        // Verify rollback - neither database should have data
        assertEquals(0, accountRepository.count());
        assertEquals(0, auditLogRepository.count());
    }

    @Test
    void testRollbackOnInsufficientBalance() {
        // Given
        CreateAccountRequest createReq1 = new CreateAccountRequest("ACC001", "Alice", new BigDecimal("100.00"));
        CreateAccountRequest createReq2 = new CreateAccountRequest("ACC002", "Bob", new BigDecimal("50.00"));
        restTemplate.postForEntity("/api/transactions/accounts", createReq1, String.class);
        restTemplate.postForEntity("/api/transactions/accounts", createReq2, String.class);
        
        auditLogRepository.deleteAll(); // Clear initial audit logs

        // When
        TransferRequest transferReq = new TransferRequest("ACC001", "ACC002", new BigDecimal("200.00"));
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/transactions/transfer", transferReq, String.class);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().contains("Insufficient balance"));

        // Verify rollback - balances should remain unchanged
        Account fromAccount = accountRepository.findByAccountNumber("ACC001").orElseThrow();
        Account toAccount = accountRepository.findByAccountNumber("ACC002").orElseThrow();

        assertEquals(0, new BigDecimal("100.00").compareTo(fromAccount.getBalance()));
        assertEquals(0, new BigDecimal("50.00").compareTo(toAccount.getBalance()));
        
        // No audit log should be created
        assertEquals(0, auditLogRepository.count());
    }

    @Test
    void testRollbackOnInvalidAccount() {
        // Given
        CreateAccountRequest createReq = new CreateAccountRequest("ACC001", "Alice", new BigDecimal("1000.00"));
        restTemplate.postForEntity("/api/transactions/accounts", createReq, String.class);

        // When
        TransferRequest transferReq = new TransferRequest("ACC001", "ACC999", new BigDecimal("100.00"));
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/transactions/transfer", transferReq, String.class);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().contains("To account not found"));

        // Verify rollback - balance should remain unchanged
        Account account = accountRepository.findByAccountNumber("ACC001").orElseThrow();
        assertEquals(0, new BigDecimal("1000.00").compareTo(account.getBalance()));
        
        // Only initial audit log should exist, no transfer log
        assertEquals(1, auditLogRepository.count());
    }

    @Test
    void testPartialFailureAtomicity() {
        // Given
        String accountNumber = "ACC888";
        
        // When - Simulate failure after PostgreSQL write but before MySQL write
        CreateAccountWithFailureRequest request = new CreateAccountWithFailureRequest(
            accountNumber, "User", new BigDecimal("100.00"), true);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/transactions/accounts/with-failure", request, String.class);

        // Then - Verify atomicity: neither database should have data due to rollback
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().contains("Simulated failure after account creation"));
        assertFalse(accountRepository.findByAccountNumber(accountNumber).isPresent());
        assertEquals(0, auditLogRepository.count());
    }
}
