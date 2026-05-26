package org.example.model;

import java.time.LocalDateTime;

public record WalletTransaction(
        String id,
        String userId,
        String transactionType,
        double amount,
        double balanceBefore,
        double balanceAfter,
        String referenceId,
        String note,
        LocalDateTime createdAt
) {
}
