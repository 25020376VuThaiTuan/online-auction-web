package org.example;

public class Launcher {
    public static void main(String[] args) {
        // Delegate through a plain main class so Maven can launch JavaFX reliably.
        App.main(args);
    }
}
