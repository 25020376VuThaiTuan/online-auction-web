package org.example;

public class Launcher {
    public static void main(String[] args) {
        // Gọi hàm main của App từ đây để "lừa" JVM
        App.main(args);
    }
}