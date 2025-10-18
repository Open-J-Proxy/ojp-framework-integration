package com.example.atomikos.dto;

import java.math.BigDecimal;

public class CreateAccountWithFailureRequest {
    private String accountNumber;
    private String accountHolder;
    private BigDecimal balance;
    private boolean failAfterAccount;

    public CreateAccountWithFailureRequest() {
    }

    public CreateAccountWithFailureRequest(String accountNumber, String accountHolder, 
                                          BigDecimal balance, boolean failAfterAccount) {
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.balance = balance;
        this.failAfterAccount = failAfterAccount;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public void setAccountHolder(String accountHolder) {
        this.accountHolder = accountHolder;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public boolean isFailAfterAccount() {
        return failAfterAccount;
    }

    public void setFailAfterAccount(boolean failAfterAccount) {
        this.failAfterAccount = failAfterAccount;
    }
}
