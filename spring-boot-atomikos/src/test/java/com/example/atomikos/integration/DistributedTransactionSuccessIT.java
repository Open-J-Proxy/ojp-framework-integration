package com.example.atomikos.integration;

import com.example.atomikos.dto.CreateAccountRequest;
import com.example.atomikos.dto.TransferRequest;
import com.example.atomikos.entity.postgres.Account;
import com.example.atomikos.entity.postgres2.AuditLog;
import com.example.atomikos.repository.postgres.AccountRepository;
import com.example.atomikos.repository.postgres2.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext
@Disabled
//Disabling until Atomikos implements support for non pooled connections, current Atomikos implementation is incompatible with OJP as per OJP must manage the pool in the proxy.
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
        String urlPostgres = postgresContainer.getJdbcUrl().replaceAll("jdbc:postgresql", "jdbc:ojp[localhost:1059(postgres)]_postgresql");
        registry.add("spring.datasource.postgres.url", () -> urlPostgres);
        registry.add("spring.datasource.postgres.username", postgresContainer::getUsername);
        registry.add("spring.datasource.postgres.password", postgresContainer::getPassword);

        String urlPostgres2 = postgres2Container.getJdbcUrl().replaceAll("jdbc:postgresql", "jdbc:ojp[localhost:1059(postgres2)]_postgresql");
        registry.add("spring.datasource.postgres2.url", () -> urlPostgres2);
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
    void testSuccessfulCommitAcrossBothDatabases() {
        // Given
        String accountNumber = "ACC001";
        String accountHolder = "John Doe";
        BigDecimal balance = new BigDecimal("1000.00");

        // When
        CreateAccountRequest request = new CreateAccountRequest(accountNumber, accountHolder, balance);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/transactions/accounts", request, String.class);

        // Then - Verify HTTP response
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        // Verify both databases have data
        List<Account> accounts = accountRepository.findAll();
        assertEquals(1, accounts.size());
        assertEquals(accountNumber, accounts.get(0).getAccountNumber());
        assertEquals(accountHolder, accounts.get(0).getAccountHolder());
        assertEquals(0, balance.compareTo(accounts.get(0).getBalance()));

        List<AuditLog> logs = auditLogRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals("CREATE_ACCOUNT", logs.get(0).getOperation());
    }

    @Test
    void testSuccessfulTransferWithAudit() {
        // Given
        CreateAccountRequest createReq1 = new CreateAccountRequest("ACC001", "Alice", new BigDecimal("1000.00"));
        CreateAccountRequest createReq2 = new CreateAccountRequest("ACC002", "Bob", new BigDecimal("500.00"));
        restTemplate.postForEntity("/api/transactions/accounts", createReq1, String.class);
        restTemplate.postForEntity("/api/transactions/accounts", createReq2, String.class);
        
        auditLogRepository.deleteAll(); // Clear initial audit logs

        // When
        BigDecimal transferAmount = new BigDecimal("250.00");
        TransferRequest transferReq = new TransferRequest("ACC001", "ACC002", transferAmount);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/transactions/transfer", transferReq, String.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
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
        for (int i = 1; i <= 3; i++) {
            CreateAccountRequest request = new CreateAccountRequest(
                "ACC00" + i, "User" + i, new BigDecimal(i * 100 + ".00"));
            ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/transactions/accounts", request, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        // Then
        assertEquals(3, accountRepository.count());
        assertEquals(3, auditLogRepository.count());
    }
}
