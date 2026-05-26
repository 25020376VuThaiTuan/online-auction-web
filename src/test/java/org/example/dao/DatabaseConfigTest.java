package org.example.dao;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
