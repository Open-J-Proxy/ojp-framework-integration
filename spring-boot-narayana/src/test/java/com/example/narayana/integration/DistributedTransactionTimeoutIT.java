package com.example.narayana.integration;

import com.example.narayana.dto.CreateAccountRequest;
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
        String urlPostgres = postgresContainer.getJdbcUrl();
        registry.add("spring.datasource.postgres.url", () -> urlPostgres);
        registry.add("spring.datasource.postgres.username", postgresContainer::getUsername);
        registry.add("spring.datasource.postgres.password", postgresContainer::getPassword);

        String urlPostgres2 = postgres2Container.getJdbcUrl();
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
    void testTransactionTimeout() {
        // Given
        String accountNumber = "ACC_TIMEOUT";
        
        // When - Transaction should timeout due to sleep
        CreateAccountRequest request = new CreateAccountRequest(
            accountNumber, "Timeout User", new BigDecimal("100.00"));
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/transactions/accounts/with-timeout", request, String.class);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        // Verify rollback occurred - no data should be persisted
        assertEquals(0, accountRepository.count());
        assertEquals(0, auditLogRepository.count());
    }
}
