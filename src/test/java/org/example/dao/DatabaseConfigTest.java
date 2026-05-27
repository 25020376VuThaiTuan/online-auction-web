package org.example.dao;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConfigTest {
    @Test
    void acceptsValidMysqlJdbcConfig() {
        assertNull(DatabaseConfig.validate(
                "jdbc:mysql://localhost:3306/auctiondb",
                "root",
                "password"
        ));
    }

    @Test
    void rejectsCombinedEnvironmentAssignmentsInJdbcUrl() {
        String problem = DatabaseConfig.validate(
                "jdbc:mysql://localhost:3306/auctiondb auction_db_user=root auction_db_password=password",
                "root",
                "password"
        );

        Assertions.assertNotNull(problem);
        assertTrue(problem.contains("separate values"));
    }

    @Test
    void rejectsInvalidMysqlConfigVariants() {
        Assertions.assertEquals(
                "AUCTION_DB_URL must be a MySQL JDBC URL, for example jdbc:mysql://localhost:3306/auctiondb.",
                DatabaseConfig.validate("jdbc:h2:mem:test", "root", "password")
        );
        assertTrue(DatabaseConfig.validate(
                "jdbc:mysql://localhost:3306/auctiondb",
                "AUCTION_DB_USER=root",
                "password"
        ).contains("another AUCTION_DB_* assignment"));
        assertTrue(DatabaseConfig.validate(
                "jdbc:mysql://localhost:3306/auctiondb",
                "root",
                "AUCTION_DB_PASSWORD=password"
        ).contains("another AUCTION_DB_* assignment"));
        Assertions.assertEquals(
                "AUCTION_DB_USER is required.",
                DatabaseConfig.validate("jdbc:mysql://localhost:3306/auctiondb", " ", "password")
        );
        Assertions.assertEquals(
                "AUCTION_DB_PASSWORD is required.",
                DatabaseConfig.validate("jdbc:mysql://localhost:3306/auctiondb", "root", " ")
        );
    }

    @Test
    void mysqlDriverCanBeLoadedWhenPresentOnClasspath() {
        assertDoesNotThrow(DatabaseConfig::loadDriver);
    }

    @Test
    void disabledPropertyPreventsEnvironmentDatabaseUse() {
        String previousValue = System.getProperty("auction.db.disabled");
        try {
            System.setProperty("auction.db.disabled", "true");

            assertFalse(DatabaseConfig.hasEnvironmentConfig());
            assertNull(DatabaseConfig.environmentProblem());
            Exception exception = assertThrows(java.sql.SQLException.class, DatabaseConfig::fromEnvironment);
            assertTrue(exception.getMessage().contains("auction.db.disabled"));
        } finally {
            if (previousValue == null) {
                System.clearProperty("auction.db.disabled");
            } else {
                System.setProperty("auction.db.disabled", previousValue);
            }
        }
    }
}
