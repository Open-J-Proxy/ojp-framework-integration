package com.example.atomikos.integration;

import com.example.atomikos.dto.CreateAccountRequest;
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
public class DistributedTransactionTimeoutIT extends AbstractAtomikosIT {

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
