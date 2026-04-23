package org.example.exception;

public class UserNotFound extends Exception {
    public UserNotFound(String message) {
        System.out.println("Error!User not found.");;
    }
}