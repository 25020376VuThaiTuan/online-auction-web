package org.example.server;

final class ApiResourceNotFoundException extends RuntimeException {
    ApiResourceNotFoundException(String message) {
        super(message);
    }
}
