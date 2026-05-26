package org.example.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class AccountInputValidator {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]{3,32}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63}$",
            Pattern.CASE_INSENSITIVE
    );
    private static final int PASSWORD_MIN_LENGTH = 6;
    private static final int PASSWORD_MAX_LENGTH = 72;
    private static final int EMAIL_MAX_LENGTH = 254;
    private static final int FULL_NAME_MAX_LENGTH = 120;
    private static final Pattern IDENTITY_COMPARISON_PATTERN = Pattern.compile("[^a-z0-9]+");

    private AccountInputValidator() {
    }

    public static RegistrationInput validateRegistration(
            String username,
            String password,
            String email,
            String fullName
    ) {
        String normalizedUsername = normalizeUsername(username);
        String rawPassword = password == null ? "" : password;
        String safePassword = rawPassword.trim();
        String safeEmail = value(email);
        String safeFullName = value(fullName);

        if (normalizedUsername.isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (!USERNAME_PATTERN.matcher(normalizedUsername).matches()) {
            throw new IllegalArgumentException("Username must be 3-32 characters and use only letters, numbers, dot, underscore, or hyphen.");
        }
        if (safeEmail.isEmpty()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (safeEmail.length() > EMAIL_MAX_LENGTH || !EMAIL_PATTERN.matcher(safeEmail).matches()) {
            throw new IllegalArgumentException("Email address format is invalid.");
        }
        if (safeFullName.isEmpty()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }
        if (!rawPassword.equals(safePassword)) {
            throw new IllegalArgumentException("Password must not start or end with whitespace.");
        }
        if (safePassword.length() < PASSWORD_MIN_LENGTH) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }
        if (safePassword.length() > PASSWORD_MAX_LENGTH) {
            throw new IllegalArgumentException("Password must be 72 characters or fewer.");
        }
        if (safeFullName.length() > FULL_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Full name must be 120 characters or fewer.");
        }
        return new RegistrationInput(normalizedUsername, safePassword, safeEmail, safeFullName);
    }

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeFullName(String fullName) {
        return value(fullName);
    }

    public static String normalizeIdentityLabel(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return IDENTITY_COMPARISON_PATTERN.matcher(normalized).replaceAll("");
    }

    private static String value(String text) {
        return text == null ? "" : text.trim();
    }

    public record RegistrationInput(String username, String password, String email, String fullName) {
    }
}
