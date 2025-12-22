package com.example.narayana.service;

import com.example.narayana.entity.postgres.Account;
import com.example.narayana.entity.postgres2.AuditLog;
import com.example.narayana.repository.postgres.AccountRepository;
import com.example.narayana.repository.postgres2.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransactionService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Transactional(rollbackFor = Exception.class)
    public void createAccountWithAudit(String accountNumber, String accountHolder, BigDecimal balance) {
        Account account = new Account(accountNumber, accountHolder, balance);
        accountRepository.save(account);
        
        AuditLog log = new AuditLog("CREATE_ACCOUNT", "Created account: " + accountNumber);
        auditLogRepository.save(log);
    }

    @Transactional(rollbackFor = Exception.class)
    public void transferWithAudit(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        Account fromAccount = accountRepository.findByAccountNumber(fromAccountNumber)
                .orElseThrow(() -> new RuntimeException("From account not found"));
        Account toAccount = accountRepository.findByAccountNumber(toAccountNumber)
                .orElseThrow(() -> new RuntimeException("To account not found"));

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        AuditLog log = new AuditLog("TRANSFER", 
            String.format("Transferred %s from %s to %s", amount, fromAccountNumber, toAccountNumber));
        auditLogRepository.save(log);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createAccountWithAuditAndFailure(String accountNumber, String accountHolder, 
                                                   BigDecimal balance, boolean failAfterAccount) {
        Account account = new Account(accountNumber, accountHolder, balance);
        accountRepository.save(account);
        
        if (failAfterAccount) {
            throw new RuntimeException("Simulated failure after account creation");
        }
        
        AuditLog log = new AuditLog("CREATE_ACCOUNT", "Created account: " + accountNumber);
        auditLogRepository.save(log);
    }

    @Transactional(timeout = 1)
    public void createAccountWithTimeout(String accountNumber, String accountHolder, BigDecimal balance) 
            throws InterruptedException {
        Account account = new Account(accountNumber, accountHolder, balance);
        accountRepository.save(account);
        
        // Simulate long-running operation
        Thread.sleep(2000);
        
        AuditLog log = new AuditLog("CREATE_ACCOUNT", "Created account: " + accountNumber);
        auditLogRepository.save(log);
    }
}
