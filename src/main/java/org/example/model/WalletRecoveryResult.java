package org.example.model;

public record WalletRecoveryResult(
        boolean accepted,
        String message,
        String email,
        String recoveryCode
) {
}
