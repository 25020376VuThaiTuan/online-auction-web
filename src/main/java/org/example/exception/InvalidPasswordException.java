package org.example.exception;

public class InvalidPasswordException extends Exception {
    public InvalidPasswordException(String message) {
        super(message);
    }
    public static void checkValid(String password) throws InvalidPasswordException {
        // Tiêu chí 1: Độ dài
        if (password == null || password.length() < 8) {
            throw new InvalidPasswordException("Lỗi: Mật khẩu phải có ít nhất 8 ký tự.");
        }

        // Tiêu chí 2: Chữ hoa & chữ thường
        if (!password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*")) {
            throw new InvalidPasswordException("Lỗi: Mật khẩu phải chứa ít nhất 1 chữ IN HOA và 1 chữ thường.");
        }

        // Tiêu chí 3: Chữ số
        if (!password.matches(".*\\d.*")) {
            throw new InvalidPasswordException("Lỗi: Mật khẩu phải chứa ít nhất 1 chữ số.");
        }
    }
}
