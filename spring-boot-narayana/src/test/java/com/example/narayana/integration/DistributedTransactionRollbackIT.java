package com.example.narayana.integration;

import com.example.narayana.dto.CreateAccountRequest;
import com.example.narayana.dto.CreateAccountWithFailureRequest;
import com.example.narayana.dto.TransferRequest;
import com.example.narayana.entity.postgres.Account;
import com.example.narayana.entity.postgres2.AuditLog;
import com.example.narayana.repository.postgres.AccountRepository;
import com.example.narayana.repository.postgres2.AuditLogRepository;
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
public class DistributedTransactionRollbackIT {

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
        String urlPostgres = postgresContainer.getJdbcUrl();
        String ojpUrlPostgres = "jdbc:ojp[" + urlPostgres + "]_postgresql://" + postgresContainer.getHost() + ":" + postgresContainer.getFirstMappedPort() + "/" + postgresContainer.getDatabaseName();
        registry.add("spring.datasource.postgres.url", () -> ojpUrlPostgres);
        registry.add("spring.datasource.postgres.username", postgresContainer::getUsername);
        registry.add("spring.datasource.postgres.password", postgresContainer::getPassword);

        String urlPostgres2 = postgres2Container.getJdbcUrl();
        String ojpUrlPostgres2 = "jdbc:ojp[" + urlPostgres2 + "]_postgresql://" + postgres2Container.getHost() + ":" + postgres2Container.getFirstMappedPort() + "/" + postgres2Container.getDatabaseName();
        registry.add("spring.datasource.postgres2.url", () -> ojpUrlPostgres2);
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
