package org.example.model;

public record PasswordRecoveryResult(
        boolean accepted,
        String message,
        String email
) {
}
