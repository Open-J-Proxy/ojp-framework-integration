package com.example.atomikos.integration;

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
import org.springframework.transaction.TransactionTimedOutException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext
public class DistributedTransactionTimeoutIT {

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
    void testTransactionTimeout() {
        // Given
        String accountNumber = "ACC_TIMEOUT";
        
        // When & Then - Transaction should timeout due to sleep
        assertThrows(Exception.class, () -> {
            transactionService.createAccountWithTimeout(accountNumber, "Timeout User", 
                new BigDecimal("100.00"));
        });

        // Verify rollback occurred - no data should be persisted
        assertEquals(0, accountRepository.count());
        assertEquals(0, auditLogRepository.count());
    }
}
