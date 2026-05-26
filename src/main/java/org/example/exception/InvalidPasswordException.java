package org.example.exception;

public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException(String message) {
        super(message);
    }

    public static void checkValid(String password) {
        if (password == null || password.length() < 8) {
            throw new InvalidPasswordException("Lỗi: Mật khẩu phải có ít nhất 8 ký tự.");
        }
        if (!password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*")) {
            throw new InvalidPasswordException("Lỗi: Mật khẩu phải chứa ít nhất 1 chữ IN HOA và 1 chữ thường.");
        }
        if (!password.matches(".*\\d.*")) {
            throw new InvalidPasswordException("Lỗi: Mật khẩu phải chứa ít nhất 1 chữ số.");
        }
    }
}