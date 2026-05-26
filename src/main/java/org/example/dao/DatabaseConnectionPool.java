package org.example.dao;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class DatabaseConnectionPool {
    private static final int DEFAULT_MAX_SIZE = 8;
    private static final long DEFAULT_BORROW_TIMEOUT_MILLIS = 5_000L;

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int maxSize;
    private final long borrowTimeoutMillis;
    private final BlockingQueue<Connection> idleConnections;
    private final AtomicInteger openConnections = new AtomicInteger();

    DatabaseConnectionPool(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.maxSize = positiveIntSetting("auction.db.maxPoolSize", "AUCTION_DB_MAX_POOL_SIZE", DEFAULT_MAX_SIZE);
        this.borrowTimeoutMillis = positiveLongSetting(
                "auction.db.borrowTimeoutMillis",
                "AUCTION_DB_BORROW_TIMEOUT_MILLIS",
                DEFAULT_BORROW_TIMEOUT_MILLIS
        );
        this.idleConnections = new ArrayBlockingQueue<>(maxSize);
    }

    Connection borrow() throws SQLException {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(borrowTimeoutMillis);

        while (true) {
            Connection idleConnection = idleConnections.poll();
            if (idleConnection != null) {
                if (isUsable(idleConnection)) {
                    return pooledConnection(idleConnection);
                }
                discard(idleConnection);
                continue;
            }

            int currentOpenConnections = openConnections.get();
            if (currentOpenConnections < maxSize
                    && openConnections.compareAndSet(currentOpenConnections, currentOpenConnections + 1)) {
                try {
                    return pooledConnection(DriverManager.getConnection(jdbcUrl, username, password));
                } catch (SQLException e) {
                    openConnections.decrementAndGet();
                    throw e;
                }
            }

            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                throw new SQLException(
                        "Timed out waiting for an available database connection. "
                                + "Increase AUCTION_DB_MAX_POOL_SIZE or reduce concurrent API requests."
                );
            }

            try {
                idleConnection = idleConnections.poll(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while waiting for a database connection.", e);
            }

            if (idleConnection == null) {
                continue;
            }
            if (isUsable(idleConnection)) {
                return pooledConnection(idleConnection);
            }
            discard(idleConnection);
        }
    }

    private Connection pooledConnection(Connection physicalConnection) {
        AtomicBoolean returned = new AtomicBoolean(false);
        InvocationHandler handler = (proxy, method, args) -> invoke(proxy, physicalConnection, returned, method, args);
        return (Connection) Proxy.newProxyInstance(
                DatabaseConnectionPool.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                handler
        );
    }

    private Object invoke(
            Object proxy,
            Connection physicalConnection,
            AtomicBoolean returned,
            Method method,
            Object[] args
    ) throws Throwable {
        String methodName = method.getName();
        if (method.getDeclaringClass() == Object.class) {
            return switch (methodName) {
                case "toString" -> "PooledConnection[" + jdbcUrl + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> method.invoke(this, args);
            };
        }
        if ("close".equals(methodName)) {
            if (returned.compareAndSet(false, true)) {
                release(physicalConnection);
            }
            return null;
        }
        if ("isClosed".equals(methodName)) {
            return returned.get() || physicalConnection.isClosed();
        }
        if ("unwrap".equals(methodName) && args != null && args.length == 1 && args[0] instanceof Class<?> type) {
            if (type.isInstance(proxy)) {
                return proxy;
            }
            if (type.isInstance(physicalConnection)) {
                return physicalConnection;
            }
        }
        if ("isWrapperFor".equals(methodName) && args != null && args.length == 1 && args[0] instanceof Class<?> type) {
            return type.isInstance(proxy) || type.isInstance(physicalConnection) || physicalConnection.isWrapperFor(type);
        }
        if (returned.get()) {
            throw new SQLException("Connection is closed.");
        }

        try {
            return method.invoke(physicalConnection, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private void release(Connection connection) {
        try {
            reset(connection);
            if (isUsable(connection) && idleConnections.offer(connection)) {
                return;
            }
        } catch (SQLException ignored) {
        }
        discard(connection);
    }

    private void reset(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.rollback();
            connection.setAutoCommit(true);
        }
        if (connection.isReadOnly()) {
            connection.setReadOnly(false);
        }
        connection.clearWarnings();
    }

    private boolean isUsable(Connection connection) {
        try {
            return !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private void discard(Connection connection) {
        closeQuietly(connection);
        openConnections.decrementAndGet();
    }

    private void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }

    private static int positiveIntSetting(String propertyName, String environmentName, int fallback) {
        String value = configuredValue(propertyName, environmentName);
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long positiveLongSetting(String propertyName, String environmentName, long fallback) {
        String value = configuredValue(propertyName, environmentName);
        if (value == null) {
            return fallback;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0L ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String configuredValue(String propertyName, String environmentName) {
        String configured = System.getProperty(propertyName);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(environmentName);
        }
        return configured == null || configured.isBlank() ? null : configured;
    }
}
