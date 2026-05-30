package org.example.exception;

import org.example.util.PasswordPolicy;

public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException(String message) {
        super(message);
    }

    public static void checkValid(String password) {
        try {
            PasswordPolicy.validate(password);
        } catch (IllegalArgumentException e) {
            throw new InvalidPasswordException(e.getMessage());
        }
    }
}
