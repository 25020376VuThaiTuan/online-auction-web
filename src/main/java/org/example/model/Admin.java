package org.example.model;

public class Admin extends User {
    public Admin(String id, String username, String passwordHash, String email) {
        super(id, username, passwordHash, email);
    }

    @Override public void displayRole() { System.out.println("Role: Quản trị viên"); }
}
