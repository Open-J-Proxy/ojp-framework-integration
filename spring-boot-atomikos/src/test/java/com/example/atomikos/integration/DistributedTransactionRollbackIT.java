package com.example.atomikos.integration;

import com.example.atomikos.entity.postgres.Account;
import com.example.atomikos.entity.mysql.AuditLog;
import com.example.atomikos.repository.postgres.AccountRepository;
import com.example.atomikos.repository.mysql.AuditLogRepository;
import com.example.atomikos.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext
public class DistributedTransactionRollbackIT {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand("postgres -c max_prepared_transactions=10");

    @Container
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.postgres.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.postgres.username", postgresContainer::getUsername);
        registry.add("spring.datasource.postgres.password", postgresContainer::getPassword);

        registry.add("spring.datasource.mysql.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.mysql.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.mysql.password", mysqlContainer::getPassword);
    }

    @Autowired
    private TransactionService transactionService;

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

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            transactionService.createAccountWithAuditAndFailure(accountNumber, accountHolder, balance, true);
        });

        assertEquals("Simulated failure after account creation", exception.getMessage());

        // Verify rollback - neither database should have data
        assertEquals(0, accountRepository.count());
        assertEquals(0, auditLogRepository.count());
    }

    @Test
    void testRollbackOnInsufficientBalance() {
        // Given
        transactionService.createAccountWithAudit("ACC001", "Alice", new BigDecimal("100.00"));
        transactionService.createAccountWithAudit("ACC002", "Bob", new BigDecimal("50.00"));
        
        auditLogRepository.deleteAll(); // Clear initial audit logs

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            transactionService.transferWithAudit("ACC001", "ACC002", new BigDecimal("200.00"));
        });

        assertEquals("Insufficient balance", exception.getMessage());

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
        transactionService.createAccountWithAudit("ACC001", "Alice", new BigDecimal("1000.00"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            transactionService.transferWithAudit("ACC001", "ACC999", new BigDecimal("100.00"));
        });

        assertEquals("To account not found", exception.getMessage());

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
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            transactionService.createAccountWithAuditAndFailure(accountNumber, "User", 
                new BigDecimal("100.00"), true);
        });

        // Then - Verify atomicity: neither database should have data due to rollback
        assertFalse(accountRepository.findByAccountNumber(accountNumber).isPresent());
        assertEquals(0, auditLogRepository.count());
    }
}
