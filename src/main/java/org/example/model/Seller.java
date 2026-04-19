package org.example.model;

public class Seller extends User {
    public Seller(String id, String username, String password, String email) {
        super(id, username, password, email);   
        }

    @Override public void displayRole() { System.out.println("Role: Người bán hàng"); }
}
