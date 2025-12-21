package com.example.narayana.dto;

import java.math.BigDecimal;

public class CreateAccountRequest {
    private String accountNumber;
    private String accountHolder;
    private BigDecimal balance;

    public CreateAccountRequest() {
    }

    public CreateAccountRequest(String accountNumber, String accountHolder, BigDecimal balance) {
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.balance = balance;
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
}
