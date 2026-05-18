package org.example.model;

import java.time.LocalDateTime;

public record WalletAuthorization(
        String token,
        LocalDateTime expiresAt
) {
}
