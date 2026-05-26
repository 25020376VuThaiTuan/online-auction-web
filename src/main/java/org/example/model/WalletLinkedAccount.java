package org.example.model;

import java.time.LocalDateTime;

public record WalletLinkedAccount(
        String id,
        String userId,
        String accountName,
        String providerName,
        String accountReference,
        double balance,
        boolean primary,
        LocalDateTime createdAt
) {
    public WalletLinkedAccount {
        balance = Double.isFinite(balance) ? Math.max(0.0, balance) : 0.0;
    }

    public String displayName() {
        String provider = providerName == null || providerName.isBlank() ? "Account" : providerName;
        String name = accountName == null || accountName.isBlank() ? "Wallet account" : accountName;
        String reference = maskedReference();
        return provider + " - " + name + (reference.isBlank() ? "" : " (" + reference + ")");
    }

    public String maskedReference() {
        if (accountReference == null || accountReference.isBlank()) {
            return "";
        }
        String trimmed = accountReference.trim();
        if (trimmed.length() <= 4) {
            return trimmed;
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }
}
