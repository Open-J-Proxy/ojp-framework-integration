package com.example.atomikos.integration;

import com.example.atomikos.dto.CreateAccountRequest;
import com.example.atomikos.dto.TransferRequest;
import com.example.atomikos.repository.postgres.AccountRepository;
import com.example.atomikos.repository.postgres2.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext
public class DistributedTransactionConnectionIT {

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
    void testSuccessfulConnectionAndTransaction() {
        // This test verifies that connections work properly with both databases
        // Given
        assertTrue(postgresContainer.isRunning());
        assertTrue(postgres2Container.isRunning());

        // When
        CreateAccountRequest request = new CreateAccountRequest(
            "ACC_CONN", "Connection Test", new BigDecimal("100.00"));
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/transactions/accounts", request, String.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, accountRepository.count());
        assertEquals(1, auditLogRepository.count());
    }

    @Test
    void testMultipleConcurrentTransactions() {
        // Test that multiple transactions can be handled properly
        // Given & When
        for (int i = 1; i <= 5; i++) {
            CreateAccountRequest request = new CreateAccountRequest(
                "ACC" + i, "User" + i, new BigDecimal(i * 100 + ".00"));
            ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/transactions/accounts", request, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        // Then
        assertEquals(5, accountRepository.count());
        assertEquals(5, auditLogRepository.count());
    }

    @Test
    void testTransactionIsolation() {
        // Create initial accounts
        CreateAccountRequest createReq1 = new CreateAccountRequest("ACC_ISO1", "User1", new BigDecimal("1000.00"));
        CreateAccountRequest createReq2 = new CreateAccountRequest("ACC_ISO2", "User2", new BigDecimal("500.00"));
        restTemplate.postForEntity("/api/transactions/accounts", createReq1, String.class);
        restTemplate.postForEntity("/api/transactions/accounts", createReq2, String.class);
        
        long initialAuditCount = auditLogRepository.count();
        
        // When - Perform transfer
        TransferRequest transferReq = new TransferRequest("ACC_ISO1", "ACC_ISO2", new BigDecimal("100.00"));
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/transactions/transfer", transferReq, String.class);
        
        // Then - Verify isolation
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(initialAuditCount + 1, auditLogRepository.count());
        
        // Verify final balances
        var acc1 = accountRepository.findByAccountNumber("ACC_ISO1").orElseThrow();
        var acc2 = accountRepository.findByAccountNumber("ACC_ISO2").orElseThrow();
        
        assertEquals(0, new BigDecimal("900.00").compareTo(acc1.getBalance()));
        assertEquals(0, new BigDecimal("600.00").compareTo(acc2.getBalance()));
    }
}
