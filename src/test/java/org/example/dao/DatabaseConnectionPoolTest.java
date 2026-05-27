package org.example.dao;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void pooledConnectionSupportsObjectAndWrapperMethods() throws Exception {
        DatabaseConnectionPool pool = newPool();

        Connection connection = pool.borrow();

        assertTrue(connection.toString().contains("PooledConnection[jdbc:h2:mem:"));
        assertEquals(System.identityHashCode(connection), connection.hashCode());
        assertEquals(connection, connection);
        assertFalse(connection.equals(new Object()));
        assertSame(connection, connection.unwrap(Connection.class));
        assertTrue(connection.isWrapperFor(Connection.class));

        connection.close();
        connection.close();
        assertTrue(connection.isClosed());
    }

    @Test
    void resetsReadOnlyStateBeforeReusingConnection() throws Exception {
        DatabaseConnectionPool pool = newPool();

        Connection connection = pool.borrow();
        connection.setReadOnly(true);
        connection.close();

        try (Connection reused = pool.borrow()) {
            assertFalse(reused.isReadOnly());
        }
    }

    @Test
    void invalidSettingsFallBackAndDriverFailuresArePropagated() throws Exception {
        withPoolProperties("not-a-number", "-1", () -> {
            DatabaseConnectionPool pool = new DatabaseConnectionPool(
                    "jdbc:h2:tcp://127.0.0.1:1/missing",
                    "sa",
                    ""
            );

            assertThrows(SQLException.class, pool::borrow);
        });
    }

    @Test
    void borrowTimesOutWhenPoolIsExhausted() throws Exception {
        withPoolProperties("1", "25", () -> {
            DatabaseConnectionPool pool = newPool();
            try {
                Connection borrowed = pool.borrow();
                try {
                    SQLException exception = assertThrows(SQLException.class, pool::borrow);
                    assertTrue(exception.getMessage().contains("Timed out waiting"));
                } finally {
                    borrowed.close();
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void interruptedBorrowRestoresInterruptFlag() throws Exception {
        withPoolProperties("1", "5000", () -> {
            DatabaseConnectionPool pool = newPool();
            try {
                Connection borrowed = pool.borrow();
                try {
                    Thread.currentThread().interrupt();
                    SQLException exception = assertThrows(SQLException.class, pool::borrow);
                    assertTrue(exception.getMessage().contains("Interrupted while waiting"));
                    assertTrue(Thread.currentThread().isInterrupted());
                } finally {
                    Thread.interrupted();
                    borrowed.close();
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void closedPhysicalConnectionIsDiscardedInsteadOfReused() throws Exception {
        withPoolProperties("1", "5000", () -> {
            DatabaseConnectionPool pool = newPool();
            try {
                Connection logical = pool.borrow();
                Connection physical = (Connection) logical.unwrap((Class) Class.forName("org.h2.jdbc.JdbcConnection"));
                physical.close();
                logical.close();

                try (Connection replacement = pool.borrow()) {
                    assertFalse(replacement.isClosed());
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private DatabaseConnectionPool newPool() throws ClassNotFoundException {
        Class.forName("org.h2.Driver");
        String databaseName = "pool_" + UUID.randomUUID().toString().replace("-", "");
        return new DatabaseConnectionPool("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    private static void withPoolProperties(String maxPoolSize, String borrowTimeoutMillis, ThrowingRunnable action) throws Exception {
        String previousMaxPoolSize = System.getProperty("auction.db.maxPoolSize");
        String previousBorrowTimeout = System.getProperty("auction.db.borrowTimeoutMillis");
        try {
            System.setProperty("auction.db.maxPoolSize", maxPoolSize);
            System.setProperty("auction.db.borrowTimeoutMillis", borrowTimeoutMillis);
            action.run();
        } finally {
            restoreProperty("auction.db.maxPoolSize", previousMaxPoolSize);
            restoreProperty("auction.db.borrowTimeoutMillis", previousBorrowTimeout);
        }
    }

    private static void restoreProperty(String propertyName, String value) {
        if (value == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, value);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
