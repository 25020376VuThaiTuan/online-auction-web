package org.example.model;

import java.io.Serializable;

public class BankAccount implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String bankName;
    private final String accountHolder;
    private final String accountNumber;

    public BankAccount(String bankName, String accountHolder, String accountNumber) {
        this.bankName = bankName == null ? "" : bankName.trim();
        this.accountHolder = accountHolder == null ? "" : accountHolder.trim();
        this.accountNumber = accountNumber == null ? "" : accountNumber.trim();
    }

    public String getBankName() {
        return bankName;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getMaskedAccountNumber() {
        if (accountNumber.length() <= 4) {
            return accountNumber;
        }
        return "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }
}
