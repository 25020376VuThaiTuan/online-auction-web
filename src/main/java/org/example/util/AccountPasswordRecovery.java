package org.example.util;

public final class AccountPasswordRecovery {
    private AccountPasswordRecovery() {
    }

    public static RecoveryIdentity validateRecoveryRequest(String username, String email) {
        String normalizedUsername = AccountInputValidator.normalizeUsername(username);
        String safeEmail = email == null ? "" : email.trim();

        if (normalizedUsername.isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (safeEmail.isEmpty()) {
            throw new IllegalArgumentException("Email is required.");
        }
        return new RecoveryIdentity(normalizedUsername, safeEmail);
    }

    public static RecoveryRequest validateResetRequest(
            String username,
            String email,
            String recoveryCode,
            String newPassword,
            String confirmPassword
    ) {
        RecoveryIdentity identity = validateRecoveryRequest(username, email);
        String safeRecoveryCode = recoveryCode == null ? "" : recoveryCode.trim();
        String safePassword = PasswordPolicy.normalize(newPassword);
        String safeConfirmPassword = PasswordPolicy.normalize(confirmPassword);

        if (!safeRecoveryCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("Password recovery code must be 6 digits.");
        }
        PasswordPolicy.validate(newPassword);
        if (!safePassword.equals(safeConfirmPassword)) {
            throw new IllegalArgumentException("Confirm password must match the new password.");
        }
        return new RecoveryRequest(identity.username(), identity.email(), safeRecoveryCode, safePassword);
    }

    public record RecoveryIdentity(String username, String email) {
    }

    public record RecoveryRequest(String username, String email, String recoveryCode, String newPassword) {
    }
}
