package org.example.model;

import java.util.ArrayList;
import java.util.List;

public abstract class User extends Entity {
    private String username;
    private String password;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String address;
    private String avatarUrl;
    private final List<BankAccount> bankAccounts = new ArrayList<>();
    protected String role;

    public User(String id, String username, String password, String email) {
        super(id);
        this.username = username;
        this.password = password;
        this.email = email;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getRole() {
        return role;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName == null || fullName.isBlank() ? username : fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName == null ? null : fullName.trim();
    }

    public String getPhoneNumber() {
        return phoneNumber == null ? "" : phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber == null ? null : phoneNumber.trim();
    }

    public String getAddress() {
        return address == null ? "" : address;
    }

    public void setAddress(String address) {
        this.address = address == null ? null : address.trim();
    }

    public String getAvatarUrl() {
        return avatarUrl == null ? "" : avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl == null ? null : avatarUrl.trim();
    }

    public List<BankAccount> getBankAccounts() {
        return new ArrayList<>(bankAccounts);
    }

    public void addBankAccount(BankAccount bankAccount) {
        if (bankAccount != null) {
            bankAccounts.add(bankAccount);
        }
    }

    public void replaceBankAccounts(List<BankAccount> accounts) {
        bankAccounts.clear();
        if (accounts != null) {
            bankAccounts.addAll(accounts);
        }
    }

    public void copyProfileFrom(User source) {
        if (source == null) {
            return;
        }
        setRole(source.getRole());
        setFullName(source.getFullName());
        setPhoneNumber(source.getPhoneNumber());
        setAddress(source.getAddress());
        setAvatarUrl(source.getAvatarUrl());
        replaceBankAccounts(source.getBankAccounts());
    }

    public abstract void displayRole();
}
