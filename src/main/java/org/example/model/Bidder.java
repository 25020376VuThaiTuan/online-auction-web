package org.example.model;

public class Bidder extends User {
    private double balance;

    public Bidder(String id, String username, String password, String email, double balance) {
        super(id, username, password, email);
        this.balance = balance;
    }
}
