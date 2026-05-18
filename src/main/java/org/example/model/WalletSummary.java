package org.example.model;

import java.util.List;

public record WalletSummary(
        String userId,
        double balance,
        double lockedBalance,
        double availableBalance,
        boolean pinSet,
        List<WalletLinkedAccount> linkedAccounts,
        List<WalletTransaction> transactions
) {
}
