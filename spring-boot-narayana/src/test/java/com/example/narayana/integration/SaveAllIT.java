package com.example.narayana.integration;

import com.example.narayana.entity.postgres.Account;
import com.example.narayana.entity.postgres2.AuditLog;
import com.example.narayana.repository.postgres.AccountRepository;
import com.example.narayana.repository.postgres2.AuditLogRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext
public class SaveAllIT {

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
    private AccountRepository accountRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setup() {
        auditLogRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void testAccountSaveAll() {
        List<Account> saved = accountRepository.saveAll(List.of(
                new Account("ACC001", "Alice", new BigDecimal("1000.00")),
                new Account("ACC002", "Bob", new BigDecimal("500.00")),
                new Account("ACC003", "Charlie", new BigDecimal("250.00"))));

        assertEquals(3, saved.size());
        saved.forEach(a -> assertNotNull(a.getId()));
    }

    @Test
    void testAccountFindAll() {
        accountRepository.saveAll(List.of(
                new Account("ACC001", "Alice", new BigDecimal("1000.00")),
                new Account("ACC002", "Bob", new BigDecimal("500.00"))));

        List<Account> all = accountRepository.findAll();

        assertEquals(2, all.size());
    }

    @Test
    void testAccountDeleteAll() {
        accountRepository.saveAll(List.of(
                new Account("ACC001", "Alice", new BigDecimal("1000.00")),
                new Account("ACC002", "Bob", new BigDecimal("500.00"))));
        assertEquals(2, accountRepository.count());

        accountRepository.deleteAll();

        assertTrue(accountRepository.findAll().isEmpty());
    }

    @Test
    void testAuditLogSaveAll() {
        List<AuditLog> saved = auditLogRepository.saveAll(List.of(
                new AuditLog("CREATE_ACCOUNT", "Created ACC001"),
                new AuditLog("CREATE_ACCOUNT", "Created ACC002")));

        assertEquals(2, saved.size());
        saved.forEach(log -> assertNotNull(log.getId()));
    }

    @Test
    void testAuditLogFindAll() {
        auditLogRepository.saveAll(List.of(
                new AuditLog("CREATE_ACCOUNT", "Created ACC001"),
                new AuditLog("TRANSFER", "ACC001 -> ACC002"),
                new AuditLog("CREATE_ACCOUNT", "Created ACC003")));

        List<AuditLog> all = auditLogRepository.findAll();

        assertEquals(3, all.size());
    }

    @Test
    void testAuditLogDeleteAll() {
        auditLogRepository.saveAll(List.of(
                new AuditLog("CREATE_ACCOUNT", "Created ACC001"),
                new AuditLog("TRANSFER", "ACC001 -> ACC002")));
        assertEquals(2, auditLogRepository.count());

        auditLogRepository.deleteAll();

        assertTrue(auditLogRepository.findAll().isEmpty());
    }
}
