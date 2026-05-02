package org.example.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class Bidder extends User {
    private double balance;
    private final Map<String, Double> lockedDepositsByAuctionId = new LinkedHashMap<>();

    public Bidder(String id, String username, String password, String email, double balance) {
        super(id, username, password, email);
        this.balance = balance;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = Math.max(0.0, balance);
    }

    public double getLockedBalance() {
        return lockedDepositsByAuctionId.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    public double getAvailableBalance() {
        return Math.max(0.0, balance - getLockedBalance());
    }

    public boolean canCoverDeposit(double amount) {
        return getAvailableBalance() >= Math.max(0.0, amount);
    }

    public void lockDeposit(String auctionId, double amount) {
        if (auctionId == null || auctionId.isBlank()) {
            return;
        }
        lockedDepositsByAuctionId.put(auctionId, Math.max(0.0, amount));
    }

    public void releaseDeposit(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return;
        }
        lockedDepositsByAuctionId.remove(auctionId);
    }

    public Map<String, Double> getLockedDepositsByAuctionId() {
        return new LinkedHashMap<>(lockedDepositsByAuctionId);
    }

    @Override
    public void displayRole() {
        System.out.println("Role: Bidder");
    }
}
