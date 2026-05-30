package org.example.util;

public final class PasswordPolicy {
    public static final int MIN_LENGTH = 6;
    public static final int MAX_LENGTH = 72;

    private PasswordPolicy() {
    }

    public static String normalize(String password) {
        return password == null ? "" : password.trim();
    }

    public static void validate(String password) {
        String rawPassword = password == null ? "" : password;
        String normalizedPassword = normalize(password);

        if (rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }
        if (!rawPassword.equals(normalizedPassword)) {
            throw new IllegalArgumentException("Password must not start or end with whitespace.");
        }
        if (normalizedPassword.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters.");
        }
        if (normalizedPassword.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Password must be " + MAX_LENGTH + " characters or fewer.");
        }
    }
}
