package org.example.dao;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
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
}
