package com.example.atomikos.integration;

import com.example.atomikos.entity.postgres.Account;
import com.example.atomikos.entity.postgres2.AuditLog;
import com.example.atomikos.repository.postgres.AccountRepository;
import com.example.atomikos.repository.postgres2.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class SaveAllIT extends AbstractAtomikosIT {

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
