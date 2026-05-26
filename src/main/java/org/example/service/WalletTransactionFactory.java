package org.example.service;

import org.example.model.User;
import org.example.model.WalletTransaction;

import java.time.LocalDateTime;
import java.util.UUID;

final class WalletTransactionFactory {
    WalletTransaction transaction(
            User user,
            String transactionType,
            double amountDelta,
            double before,
            double after,
            String referenceId,
            String note
    ) {
        return new WalletTransaction(
                UUID.randomUUID().toString(),
                user.getId(),
                transactionType,
                Math.abs(amountDelta),
                before,
                after,
                referenceId,
                note == null ? "" : note,
                LocalDateTime.now()
        );
    }

    WalletTransaction accountAdjustment(
            User user,
            double amount,
            double balance,
            String referenceId,
            String note
    ) {
        return new WalletTransaction(
                UUID.randomUUID().toString(),
                user.getId(),
                "ADJUSTMENT",
                roundCurrency(amount),
                balance,
                balance,
                referenceId,
                note == null ? "" : note,
                LocalDateTime.now()
        );
    }

    private double roundCurrency(double amount) {
        return Math.round(Math.max(0.0, amount) * 100.0) / 100.0;
    }
}
