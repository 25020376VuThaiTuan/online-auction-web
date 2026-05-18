package org.example.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;

public record DatabaseConfig(String jdbcUrl, String username, String password) {
    private static final String URL_ENV = "AUCTION_DB_URL";
    private static final String USER_ENV = "AUCTION_DB_USER";
    private static final String PASSWORD_ENV = "AUCTION_DB_PASSWORD";
    private static final String JDBC_MYSQL_PREFIX = "jdbc:mysql://";

    public static boolean hasEnvironmentConfig() {
        return hasText(System.getenv(URL_ENV))
                && hasText(System.getenv(USER_ENV))
                && hasText(System.getenv(PASSWORD_ENV));
    }

    public static String environmentProblem() {
        String jdbcUrl = System.getenv(URL_ENV);
        String username = System.getenv(USER_ENV);
        String password = System.getenv(PASSWORD_ENV);

        if (!hasText(jdbcUrl) && !hasText(username) && !hasText(password)) {
            return null;
        }
        if (!hasText(jdbcUrl) || !hasText(username) || !hasText(password)) {
            return "Database config is incomplete. Set AUCTION_DB_URL, AUCTION_DB_USER, and AUCTION_DB_PASSWORD separately.";
        }
        return validate(jdbcUrl, username, password);
    }

    public static DatabaseConfig fromEnvironment() throws SQLException {
        String problem = environmentProblem();
        if (problem != null) {
            throw new SQLException(problem);
        }
        if (!hasEnvironmentConfig()) {
            throw new SQLException("Database config is not set. Set AUCTION_DB_URL, AUCTION_DB_USER, and AUCTION_DB_PASSWORD.");
        }
        return new DatabaseConfig(
                System.getenv(URL_ENV).trim(),
                System.getenv(USER_ENV).trim(),
                System.getenv(PASSWORD_ENV)
        );
    }

    public Connection openConnection() throws SQLException {
        loadDriver();
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    public static String validate(String jdbcUrl, String username, String password) {
        String trimmedUrl = jdbcUrl == null ? "" : jdbcUrl.trim();
        if (!trimmedUrl.startsWith(JDBC_MYSQL_PREFIX)) {
            return "AUCTION_DB_URL must be a MySQL JDBC URL, for example jdbc:mysql://localhost:3306/auctiondb.";
        }
        if (containsWhitespace(trimmedUrl)) {
            return "AUCTION_DB_URL contains spaces. Set AUCTION_DB_URL, AUCTION_DB_USER, and AUCTION_DB_PASSWORD as separate values.";
        }
        if (looksLikeCombinedAssignments(trimmedUrl)
                || looksLikeCombinedAssignments(username)
                || looksLikeCombinedAssignments(password)) {
            return "Database config contains another AUCTION_DB_* assignment. Set each database environment variable separately.";
        }
        if (!hasText(username)) {
            return "AUCTION_DB_USER is required.";
        }
        if (!hasText(password)) {
            return "AUCTION_DB_PASSWORD is required.";
        }
        return null;
    }

    static void loadDriver() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Could not find JDBC driver", e);
        }
    }

    private static boolean looksLikeCombinedAssignments(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        return normalized.contains("AUCTION_DB_URL=")
                || normalized.contains("AUCTION_DB_USER=")
                || normalized.contains("AUCTION_DB_PASSWORD=");
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
