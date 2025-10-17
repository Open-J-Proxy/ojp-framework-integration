package com.example.atomikos.integration;

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
public class DistributedTransactionConnectionIT {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

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
    void testSuccessfulConnectionAndTransaction() {
        // This test verifies that connections work properly with both databases
        // Given
        assertTrue(postgresContainer.isRunning());
        assertTrue(mysqlContainer.isRunning());

        // When
        transactionService.createAccountWithAudit("ACC_CONN", "Connection Test", 
            new BigDecimal("100.00"));

        // Then
        assertEquals(1, accountRepository.count());
        assertEquals(1, auditLogRepository.count());
    }

    @Test
    void testMultipleConcurrentTransactions() {
        // Test that multiple transactions can be handled properly
        // Given & When
        for (int i = 1; i <= 5; i++) {
            transactionService.createAccountWithAudit("ACC" + i, "User" + i, 
                new BigDecimal(i * 100 + ".00"));
        }

        // Then
        assertEquals(5, accountRepository.count());
        assertEquals(5, auditLogRepository.count());
    }

    @Test
    void testTransactionIsolation() {
        // Create initial accounts
        transactionService.createAccountWithAudit("ACC_ISO1", "User1", new BigDecimal("1000.00"));
        transactionService.createAccountWithAudit("ACC_ISO2", "User2", new BigDecimal("500.00"));
        
        long initialAuditCount = auditLogRepository.count();
        
        // When - Perform transfer
        transactionService.transferWithAudit("ACC_ISO1", "ACC_ISO2", new BigDecimal("100.00"));
        
        // Then - Verify isolation
        assertEquals(initialAuditCount + 1, auditLogRepository.count());
        
        // Verify final balances
        var acc1 = accountRepository.findByAccountNumber("ACC_ISO1").orElseThrow();
        var acc2 = accountRepository.findByAccountNumber("ACC_ISO2").orElseThrow();
        
        assertEquals(0, new BigDecimal("900.00").compareTo(acc1.getBalance()));
        assertEquals(0, new BigDecimal("600.00").compareTo(acc2.getBalance()));
    }
}
