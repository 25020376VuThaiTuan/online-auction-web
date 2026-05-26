package org.example.dao;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConnectionPoolTest {
    @Test
    void returnsLogicalConnectionToPoolOnClose() throws Exception {
        DatabaseConnectionPool pool = newPool();

        Connection first = pool.borrow();
        try (Statement statement = first.createStatement()) {
            statement.execute("CREATE TABLE entries (id INT PRIMARY KEY)");
            statement.executeUpdate("INSERT INTO entries (id) VALUES (1)");
        }
        first.close();

        assertTrue(first.isClosed());
        assertThrows(SQLException.class, first::createStatement);

        try (Connection second = pool.borrow();
             Statement statement = second.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM entries")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    @Test
    void rollsBackUncommittedWorkBeforeReusingConnection() throws Exception {
        DatabaseConnectionPool pool = newPool();
        try (Connection connection = pool.borrow();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE entries (id INT PRIMARY KEY)");
        }

        Connection connection = pool.borrow();
        try (Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("INSERT INTO entries (id) VALUES (1)");
        }
        connection.close();

        try (Connection reused = pool.borrow();
             Statement statement = reused.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM entries")) {
            assertTrue(resultSet.next());
            assertEquals(0, resultSet.getInt(1));
            assertTrue(reused.getAutoCommit());
        }
    }

    private DatabaseConnectionPool newPool() throws ClassNotFoundException {
        Class.forName("org.h2.Driver");
        String databaseName = "pool_" + UUID.randomUUID().toString().replace("-", "");
        return new DatabaseConnectionPool("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1", "sa", "");
    }
}
