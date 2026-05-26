package org.example.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountInputValidatorTest {

    @Test
    void validateRegistration_ValidInput_ReturnsRegistrationInput() {

        AccountInputValidator.RegistrationInput result =
                AccountInputValidator.validateRegistration(
                        "john_doe",
                        "password123",
                        "john@gmail.com",
                        "John Doe"
                );

        assertEquals("john_doe", result.username());
        assertEquals("password123", result.password());
        assertEquals("john@gmail.com", result.email());
        assertEquals("John Doe", result.fullName());
    }

    @Test
    void validateRegistration_EmptyUsername_ThrowsException() {

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AccountInputValidator.validateRegistration(
                        "",
                        "password123",
                        "john@gmail.com",
                        "John Doe"
                )
        );

        assertEquals("Username is required.", ex.getMessage());
    }

    @Test
    void validateRegistration_InvalidUsername_ThrowsException() {

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AccountInputValidator.validateRegistration(
                        "JOHN@123",
                        "password123",
                        "john@gmail.com",
                        "John Doe"
                )
        );

        assertTrue(ex.getMessage().contains("Username must be"));
    }

    @Test
    void validateRegistration_EmptyEmail_ThrowsException() {

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AccountInputValidator.validateRegistration(
                        "john123",
                        "password123",
                        "",
                        "John Doe"
                )
        );

        assertEquals("Email is required.", ex.getMessage());
    }

    @Test
    void validateRegistration_InvalidEmail_ThrowsException() {

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AccountInputValidator.validateRegistration(
                        "john123",
                        "password123",
                        "invalid-email",
                        "John Doe"
                )
        );

        assertEquals("Email address format is invalid.", ex.getMessage());
    }

    @Test
    void validateRegistration_EmptyFullName_ThrowsException() {

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AccountInputValidator.validateRegistration(
                        "john123",
                        "password123",
                        "john@gmail.com",
                        ""
                )
        );

        assertEquals("Full name is required.", ex.getMessage());
    }

    @Test
    void validateRegistration_BlankPassword_ThrowsException() {

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AccountInputValidator.validateRegistration(
                        "john123",
                        "   ",
                        "john@gmail.com",
                        "John Doe"
                )
        );

        assertEquals("Password is required.", ex.getMessage());
    }

    @Test
    void validateRegistration_PasswordWithSpaces_ThrowsException() {

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AccountInputValidator.validateRegistration(
                        "john123",
                        " password123 ",
                        "john@gmail.com",
                        "John Doe"
                )
        );

        assertEquals(
                "Password must not start or end with whitespace.",
                ex.getMessage()
        );
    }

    @Test
    void validateRegistration_ShortPassword_ThrowsException() {

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AccountInputValidator.validateRegistration(
                        "john123",
                        "123",
                        "john@gmail.com",
                        "John Doe"
                )
        );

        assertEquals(
                "Password must be at least 6 characters.",
                ex.getMessage()
        );
    }

    @Test
    void validateRegistration_LongPassword_ThrowsException() {

        String longPassword = "a".repeat(73);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AccountInputValidator.validateRegistration(
                        "john123",
                        longPassword,
                        "john@gmail.com",
                        "John Doe"
                )
        );

        assertEquals(
                "Password must be 72 characters or fewer.",
                ex.getMessage()
        );
    }

    @Test
    void validateRegistration_LongFullName_ThrowsException() {

        String longName = "a".repeat(121);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AccountInputValidator.validateRegistration(
                        "john123",
                        "password123",
                        "john@gmail.com",
                        longName
                )
        );

        assertEquals(
                "Full name must be 120 characters or fewer.",
                ex.getMessage()
        );
    }

    @Test
    void normalizeUsername_ReturnsLowerCaseTrimmed() {

        String result = AccountInputValidator.normalizeUsername("  John_Doe  ");

        assertEquals("john_doe", result);
    }

    @Test
    void normalizeFullName_ReturnsTrimmedValue() {

        String result = AccountInputValidator.normalizeFullName("  John Doe  ");

        assertEquals("John Doe", result);
    }

    @Test
    void normalizeIdentityLabel_RemovesSpecialCharacters() {

        String result = AccountInputValidator.normalizeIdentityLabel(
                " John-Doe_123 "
        );

        assertEquals("johndoe123", result);
    }
}