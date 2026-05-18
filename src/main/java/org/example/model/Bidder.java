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

    public synchronized double getBalance() {
        return balance;
    }

    public synchronized void setBalance(double balance) {
        this.balance = Math.max(0.0, balance);
    }

    public synchronized double getLockedBalance() {
        return lockedDepositsByAuctionId.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    public synchronized double getAvailableBalance() {
        return Math.max(0.0, balance - getLockedBalance());
    }

    public synchronized boolean canCoverDeposit(double amount) {
        return getAvailableBalance() >= Math.max(0.0, amount);
    }

    public synchronized void lockDeposit(String auctionId, double amount) {
        if (auctionId == null || auctionId.isBlank()) {
            return;
        }
        lockedDepositsByAuctionId.put(auctionId, Math.max(0.0, amount));
    }

    public synchronized double getLockedAmount(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return 0.0;
        }
        return lockedDepositsByAuctionId.getOrDefault(auctionId, 0.0);
    }

    public synchronized void releaseDeposit(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return;
        }
        lockedDepositsByAuctionId.remove(auctionId);
    }

    public synchronized double captureDeposit(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return 0.0;
        }
        double amount = lockedDepositsByAuctionId.getOrDefault(auctionId, 0.0);
        lockedDepositsByAuctionId.remove(auctionId);
        setBalance(balance - amount);
        return amount;
    }

    public synchronized void replaceLockedDeposits(Map<String, Double> lockedDeposits) {
        lockedDepositsByAuctionId.clear();
        if (lockedDeposits == null) {
            return;
        }
        for (Map.Entry<String, Double> entry : lockedDeposits.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
                lockedDepositsByAuctionId.put(entry.getKey(), Math.max(0.0, entry.getValue()));
            }
        }
    }

    public synchronized Map<String, Double> getLockedDepositsByAuctionId() {
        return new LinkedHashMap<>(lockedDepositsByAuctionId);
    }

    @Override
    public void displayRole() {
        System.out.println("Role: Bidder");
    }
}
