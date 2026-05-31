package org.example.model;

public abstract class User extends Entity {
    private String username;
    private String passwordHash;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String address;
    private String avatarUrl;
    private boolean accountBanned;
    protected String role;

    public User(String id, String username, String passwordHash, String email) {
        super(id);
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
    }

    public void setRole(String role) {
        this.role = role == null ? null : role.trim().toUpperCase();
    }

    public String getRole() {
        return role;
    }

    @Override
    public String getId() {
        return super.getId();
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
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

    public boolean isAccountBanned() {
        return accountBanned;
    }

    public void setAccountBanned(boolean accountBanned) {
        this.accountBanned = accountBanned;
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
        setAccountBanned(source.isAccountBanned());
    }

    public abstract void displayRole();
}
