package org.example.exception;

public class AccountBannedException extends InvalidPasswordException {
    public AccountBannedException(String message) {
        super(message);
    }
}
