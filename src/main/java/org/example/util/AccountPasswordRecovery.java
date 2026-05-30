package org.example.util;

public final class AccountPasswordRecovery {
    private AccountPasswordRecovery() {
    }

    public static RecoveryRequest validateResetRequest(
            String username,
            String email,
            String newPassword,
            String confirmPassword
    ) {
        String normalizedUsername = AccountInputValidator.normalizeUsername(username);
        String safeEmail = email == null ? "" : email.trim();
        String safePassword = PasswordPolicy.normalize(newPassword);
        String safeConfirmPassword = PasswordPolicy.normalize(confirmPassword);

        if (normalizedUsername.isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (safeEmail.isEmpty()) {
            throw new IllegalArgumentException("Email is required.");
        }
        PasswordPolicy.validate(newPassword);
        if (!safePassword.equals(safeConfirmPassword)) {
            throw new IllegalArgumentException("Confirm password must match the new password.");
        }
        return new RecoveryRequest(normalizedUsername, safeEmail, safePassword);
    }

    public record RecoveryRequest(String username, String email, String newPassword) {
    }
}
