package com.example.atomikos.integration;

import com.example.atomikos.entity.postgres.Account;
import com.example.atomikos.entity.postgres2.AuditLog;
import com.example.atomikos.repository.postgres.AccountRepository;
import com.example.atomikos.repository.postgres2.AuditLogRepository;
import com.example.atomikos.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext
public class DistributedTransactionSuccessIT {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("accountsdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand("postgres -c max_prepared_transactions=10");

    @Container
    static PostgreSQLContainer<?> postgres2Container = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("auditdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand("postgres -c max_prepared_transactions=10");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.postgres.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.postgres.username", postgresContainer::getUsername);
        registry.add("spring.datasource.postgres.password", postgresContainer::getPassword);

        registry.add("spring.datasource.postgres2.url", postgres2Container::getJdbcUrl);
        registry.add("spring.datasource.postgres2.username", postgres2Container::getUsername);
        registry.add("spring.datasource.postgres2.password", postgres2Container::getPassword);
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
    void testSuccessfulCommitAcrossBothDatabases() {
        // Given
        String accountNumber = "ACC001";
        String accountHolder = "John Doe";
        BigDecimal balance = new BigDecimal("1000.00");

        // When
        transactionService.createAccountWithAudit(accountNumber, accountHolder, balance);

        // Then - Verify both databases have data
        List<Account> accounts = accountRepository.findAll();
        assertEquals(1, accounts.size());
        assertEquals(accountNumber, accounts.get(0).getAccountNumber());
        assertEquals(accountHolder, accounts.get(0).getAccountHolder());
        assertEquals(0, balance.compareTo(accounts.get(0).getBalance()));

        List<AuditLog> logs = auditLogRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals("CREATE_ACCOUNT", logs.get(0).getOperation());
        assertTrue(logs.get(0).getDetails().contains(accountNumber));
    }

    @Test
    void testSuccessfulTransferWithAudit() {
        // Given
        transactionService.createAccountWithAudit("ACC001", "Alice", new BigDecimal("1000.00"));
        transactionService.createAccountWithAudit("ACC002", "Bob", new BigDecimal("500.00"));
        
        auditLogRepository.deleteAll(); // Clear initial audit logs

        // When
        BigDecimal transferAmount = new BigDecimal("250.00");
        transactionService.transferWithAudit("ACC001", "ACC002", transferAmount);

        // Then
        Account fromAccount = accountRepository.findByAccountNumber("ACC001").orElseThrow();
        Account toAccount = accountRepository.findByAccountNumber("ACC002").orElseThrow();

        assertEquals(0, new BigDecimal("750.00").compareTo(fromAccount.getBalance()));
        assertEquals(0, new BigDecimal("750.00").compareTo(toAccount.getBalance()));

        List<AuditLog> logs = auditLogRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals("TRANSFER", logs.get(0).getOperation());
        assertTrue(logs.get(0).getDetails().contains("ACC001"));
        assertTrue(logs.get(0).getDetails().contains("ACC002"));
    }

    @Test
    void testMultipleSuccessfulTransactions() {
        // Given & When
        transactionService.createAccountWithAudit("ACC001", "User1", new BigDecimal("100.00"));
        transactionService.createAccountWithAudit("ACC002", "User2", new BigDecimal("200.00"));
        transactionService.createAccountWithAudit("ACC003", "User3", new BigDecimal("300.00"));

        // Then
        assertEquals(3, accountRepository.count());
        assertEquals(3, auditLogRepository.count());
    }
}
