package org.example.server;

import com.sun.net.httpserver.HttpServer;
import org.example.dao.DatabaseConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionApiServerMainTest {
    @AfterEach
    void clearProperties() {
        System.clearProperty("auction.api.workerThreads");
        System.clearProperty("auction.api.port");
        System.clearProperty("auction.api.virtualThreads");
    }

    @Test
    void resolveWorkerThreadsUsesPositiveSystemProperty() {
        System.setProperty("auction.api.workerThreads", "6");

        assertEquals(6, AuctionApiServerMain.resolveWorkerThreads());
    }

    @Test
    void resolveWorkerThreadsFallsBackForInvalidValues() {
        System.setProperty("auction.api.workerThreads", "-2");

        assertEquals(16, AuctionApiServerMain.resolveWorkerThreads());
    }

    @Test
    void resolvePortUsesArgsPropertiesAndDefaults() {
        System.clearProperty("auction.api.port");

        assertEquals(9090, AuctionApiServerMain.resolvePort(new String[]{"--port=9090"}));
        assertEquals(9091, AuctionApiServerMain.resolvePort(new String[]{"--port", "9091"}));
        assertEquals(8081, AuctionApiServerMain.resolvePort(new String[]{"--port=-1"}));
        assertEquals(8081, AuctionApiServerMain.resolvePort(new String[]{"--port=bad"}));
        assertEquals(8081, AuctionApiServerMain.resolvePortSelection(null, Map.of()).port());
        assertEquals(8082, AuctionApiServerMain.resolvePortSelection(
                new String[0],
                Map.of("AUCTION_API_PORT", "8082")
        ).port());
        assertEquals(8083, AuctionApiServerMain.resolvePortSelection(
                new String[0],
                Map.of("PORT", "8083")
        ).port());
        assertEquals(8084, AuctionApiServerMain.resolvePortSelection(
                new String[0],
                Map.of("WEBSITES_PORT", "8084")
        ).port());
        assertEquals(8085, AuctionApiServerMain.resolvePortSelection(
                new String[0],
                Map.of("CONTAINER_APP_PORT", "8085")
        ).port());

        System.setProperty("auction.api.port", "9092");
        assertEquals(9092, AuctionApiServerMain.resolvePort(new String[0]));
    }

    @Test
    void requestExecutorCanUseNamedPlatformWorkersWhenVirtualThreadsDisabled() throws Exception {
        System.setProperty("auction.api.virtualThreads", "false");
        System.setProperty("auction.api.workerThreads", "1");
        ExecutorService executor = AuctionApiServerMain.createRequestExecutor();
        try {
            Future<String> threadName = executor.submit(() -> {
                Thread current = Thread.currentThread();
                assertTrue(current.isDaemon());
                return current.getName();
            });

            assertTrue(threadName.get().startsWith("auction-api-worker-"));
        } finally {
            executor.shutdownNow();
            System.clearProperty("auction.api.virtualThreads");
        }
    }

    @Test
    void requestExecutorUsesVirtualThreadsByDefault() throws Exception {
        System.clearProperty("auction.api.virtualThreads");
        ExecutorService executor = AuctionApiServerMain.createRequestExecutor();
        try {
            Future<Boolean> virtual = executor.submit(() -> Thread.currentThread().isVirtual());

            assertTrue(virtual.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void createServerSupportsExplicitAndFallbackPortsAndReportsExplicitConflicts()
            throws Exception {

        HttpServer explicit = invokeCreateServer(new AuctionApiServerMain.PortSelection(0, true));
        int explicitPort = explicit.getAddress().getPort();
        assertTrue(explicitPort > 0);
        explicit.stop(0);

        HttpServer fallback = invokeCreateServer(new AuctionApiServerMain.PortSelection(0, false));
        assertTrue(fallback.getAddress().getPort() > 0);
        fallback.stop(0);

        HttpServer occupied = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
        int occupiedPort = occupied.getAddress().getPort();
        try {
            IOException exception = assertThrows(
                    IOException.class,
                    () -> invokeCreateServer(new AuctionApiServerMain.PortSelection(occupiedPort, true))
            );
            assertTrue(exception.getMessage().contains("already in use"));
            assertTrue(exception.getMessage().contains("--port"));
        } finally {
            occupied.stop(0);
        }
    }

    @Test
    void startupPreflightSkipsDatabaseConnectionWhenNoDatabaseEnvironmentIsSet() throws Exception {
        AtomicBoolean verifierCalled = new AtomicBoolean(false);

        AuctionApiServerMain.runStartupPreflight(Map.of(), config -> verifierCalled.set(true));

        assertFalse(verifierCalled.get());
    }

    @Test
    void startupPreflightRejectsIncompleteDatabaseEnvironment() {
        IOException exception = assertThrows(
                IOException.class,
                () -> AuctionApiServerMain.runStartupPreflight(
                        Map.of("AUCTION_DB_URL", "jdbc:mysql://db.example.com:3306/auctiondb"),
                        config -> {
                        }
                )
        );

        assertTrue(exception.getMessage().contains("AUCTION_DB_URL"));
        assertTrue(exception.getMessage().contains("AUCTION_DB_USER"));
        assertTrue(exception.getMessage().contains("AUCTION_DB_PASSWORD"));
    }

    @Test
    void startupPreflightRejectsMalformedDatabaseUrlBeforeConnecting() {
        AtomicBoolean verifierCalled = new AtomicBoolean(false);

        IOException exception = assertThrows(
                IOException.class,
                () -> AuctionApiServerMain.runStartupPreflight(
                        databaseEnvironment("https://db.example.com/auctiondb", "auction", "secret"),
                        config -> verifierCalled.set(true)
                )
        );

        assertTrue(exception.getMessage().contains("AUCTION_DB_URL must be a MySQL JDBC URL"));
        assertFalse(verifierCalled.get());
    }

    @Test
    void startupPreflightRejectsCombinedDatabaseAssignmentsBeforeConnecting() {
        AtomicBoolean verifierCalled = new AtomicBoolean(false);

        IOException exception = assertThrows(
                IOException.class,
                () -> AuctionApiServerMain.runStartupPreflight(
                        databaseEnvironment(
                                "jdbc:mysql://db.example.com:3306/auctiondb AUCTION_DB_USER=root",
                                "auction",
                                "secret"
                        ),
                        config -> verifierCalled.set(true)
                )
        );

        assertTrue(exception.getMessage().contains("separate values"));
        assertFalse(verifierCalled.get());
    }

    @Test
    void startupPreflightSurfacesRemoteMysqlAuthenticationErrors() {
        IOException exception = assertThrows(
                IOException.class,
                () -> AuctionApiServerMain.runStartupPreflight(
                        databaseEnvironment("jdbc:mysql://db.example.com:3306/auctiondb", "auction", "wrong"),
                        config -> {
                            throw new SQLException("Access denied for user 'auction'", "28000", 1045);
                        }
                )
        );

        assertTrue(exception.getMessage().contains("MySQL authentication failed"));
        assertTrue(exception.getMessage().contains("AUCTION_DB_USER"));
        assertTrue(exception.getMessage().contains("AUCTION_DB_PASSWORD"));
        assertTrue(exception.getMessage().contains("Access denied"));
    }

    @Test
    void startupPreflightReportsPublicKeyRetrievalAndGenericFailures() {
        IOException publicKeyException = assertThrows(
                IOException.class,
                () -> AuctionApiServerMain.runStartupPreflight(
                        databaseEnvironment("jdbc:mysql://db.example.com:3306/auctiondb", "auction", "secret"),
                        config -> {
                            throw new SQLException("Public Key Retrieval is not allowed", "08001", 0);
                        }
                )
        );
        assertTrue(publicKeyException.getMessage().contains("allowPublicKeyRetrieval=true"));

        SQLException generic = new SQLException("", "HY000", 0);
        generic.setNextException(new SQLException("next useful message"));
        assertTrue(AuctionApiServerMain.remoteMysqlFailureMessage(generic).contains("next useful message"));
    }

    @Test
    void startupPreflightRepairsSupportedSchemaBeforeFailing() throws Exception {
        AtomicBoolean upgraderCalled = new AtomicBoolean(false);
        AtomicInteger verifierCalls = new AtomicInteger(0);

        AuctionApiServerMain.runStartupPreflight(
                databaseEnvironment("jdbc:mysql://db.example.com:3306/auctiondb", "auction", "secret"),
                config -> {
                    if (verifierCalls.getAndIncrement() == 0) {
                        throw new SQLException("Missing required table 'auto_bids'", "42S02", 1146);
                    }
                },
                (config, cause) -> {
                    upgraderCalled.set(true);
                    assertTrue(cause.getMessage().contains("auto_bids"));
                    return true;
                }
        );

        assertTrue(upgraderCalled.get());
        assertEquals(2, verifierCalls.get());
    }

    @Test
    void startupPreflightDoesNotAttemptSchemaRepairForAuthenticationErrors() {
        AtomicBoolean upgraderCalled = new AtomicBoolean(false);

        IOException exception = assertThrows(
                IOException.class,
                () -> AuctionApiServerMain.runStartupPreflight(
                        databaseEnvironment("jdbc:mysql://db.example.com:3306/auctiondb", "auction", "wrong"),
                        config -> {
                            throw new SQLException("Access denied for user 'auction'", "28000", 1045);
                        },
                        (config, cause) -> {
                            upgraderCalled.set(true);
                            return true;
                        }
                )
        );

        assertFalse(upgraderCalled.get());
        assertTrue(exception.getMessage().contains("MySQL authentication failed"));
    }

    @Test
    void startupPreflightSurfacesSchemaRepairFalseAndRepairFailure() {
        IOException unrepaired = assertThrows(
                IOException.class,
                () -> AuctionApiServerMain.runStartupPreflight(
                        databaseEnvironment("jdbc:mysql://db.example.com:3306/auctiondb", "auction", "secret"),
                        config -> {
                            throw new SQLException("Missing required column 'items.status'", "42S22", 1054);
                        },
                        (config, cause) -> false
                )
        );
        assertTrue(unrepaired.getMessage().contains("Remote MySQL schema is incomplete"));

        IOException repairFailed = assertThrows(
                IOException.class,
                () -> AuctionApiServerMain.runStartupPreflight(
                        databaseEnvironment("jdbc:mysql://db.example.com:3306/auctiondb", "auction", "secret"),
                        config -> {
                            throw new SQLException("Missing required table 'wallet_accounts'", "42S02", 1146);
                        },
                        (config, cause) -> {
                            throw new SQLException("repair failed", "42000", 0);
                        }
                )
        );
        assertTrue(repairFailed.getMessage().contains("repair failed"));
    }

    @Test
    void walletTransactionSchemaAcceptsModernColumns() {
        assertTrue(AuctionApiServerMain.isWalletTransactionSchemaCompatible(List.of(
                "id",
                "user_id",
                "reference_id",
                "transaction_type",
                "amount",
                "balance_before",
                "balance_after",
                "note",
                "created_at"
        )));
    }

    @Test
    void walletTransactionSchemaAcceptsLegacyColumns() {
        assertTrue(AuctionApiServerMain.isWalletTransactionSchemaCompatible(List.of(
                "id",
                "bidder_id",
                "auction_id",
                "payment_id",
                "transaction_type",
                "amount",
                "balance_before",
                "balance_after",
                "note",
                "created_at"
        )));
    }

    @Test
    void walletTransactionSchemaRejectsUnsupportedColumns() {
        assertFalse(AuctionApiServerMain.isWalletTransactionSchemaCompatible(List.of(
                "id",
                "transaction_type",
                "amount",
                "created_at"
        )));
    }

    @Test
    void walletTransactionSchemaVerificationRejectsMissingAndUnsupportedTables()
            throws Exception {

        try (Connection connection = h2("server_main_missing_wallet_tx")) {
            SQLException missing = assertThrows(
                    SQLException.class,
                    () -> AuctionApiServerMain.verifyWalletTransactionSchema(connection)
            );
            assertTrue(missing.getMessage().contains("Missing required table 'wallet_transactions'"));
        }

        try (Connection connection = h2("server_main_bad_wallet_tx");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE wallet_transactions (
                        id VARCHAR(36),
                        transaction_type VARCHAR(50),
                        amount DECIMAL(15, 2),
                        created_at TIMESTAMP
                    )
                    """);

            SQLException unsupported = assertThrows(
                    SQLException.class,
                    () -> AuctionApiServerMain.verifyWalletTransactionSchema(connection)
            );
            assertTrue(unsupported.getMessage().contains("modern columns"));
            assertTrue(unsupported.getMessage().contains("legacy columns"));
        }
    }

    @Test
    void walletRecoveryColumnVerificationRejectsMissingAndShortColumns()
            throws Exception {

        try (Connection connection = h2("server_main_missing_recovery");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE wallet_accounts (
                        user_id VARCHAR(36) PRIMARY KEY,
                        balance DECIMAL(15, 2)
                    )
                    """);

            SQLException missing = assertThrows(
                    SQLException.class,
                    () -> AuctionApiServerMain.verifyWalletRecoveryCodeColumn(connection)
            );
            assertTrue(missing.getMessage().contains("pin_recovery_code"));
        }

        try (Connection connection = h2("server_main_short_recovery");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE wallet_accounts (
                        user_id VARCHAR(36) PRIMARY KEY,
                        pin_recovery_code VARCHAR(64)
                    )
                    """);

            SQLException shortColumn = assertThrows(
                    SQLException.class,
                    () -> AuctionApiServerMain.verifyWalletRecoveryCodeColumn(connection)
            );
            assertTrue(shortColumn.getMessage().contains("at least 255 characters"));
        }
    }

    @Test
    void startupPreflightSurfacesRemoteMysqlNetworkErrors() {
        IOException exception = assertThrows(
                IOException.class,
                () -> AuctionApiServerMain.runStartupPreflight(
                        databaseEnvironment("jdbc:mysql://db.example.com:3306/auctiondb", "auction", "secret"),
                        config -> {
                            throw new SQLException("Communications link failure", "08S01", 0);
                        }
                )
        );

        assertTrue(exception.getMessage().contains("Could not reach the remote MySQL server"));
        assertTrue(exception.getMessage().contains("host"));
        assertTrue(exception.getMessage().contains("port"));
        assertTrue(exception.getMessage().contains("firewall"));
    }

    @Test
    void startupPreflightSurfacesUnknownDatabaseErrors() {
        IOException exception = assertThrows(
                IOException.class,
                () -> AuctionApiServerMain.runStartupPreflight(
                        databaseEnvironment("jdbc:mysql://db.example.com:3306/missingdb", "auction", "secret"),
                        config -> {
                            throw new SQLException("Unknown database 'missingdb'", "42000", 1049);
                        }
                )
        );

        assertTrue(exception.getMessage().contains("database named in AUCTION_DB_URL does not exist"));
        assertTrue(exception.getMessage().contains("Unknown database"));
    }

    @Test
    void startupPreflightSurfacesIncompleteSchemaErrors() {
        IOException exception = assertThrows(
                IOException.class,
                () -> AuctionApiServerMain.runStartupPreflight(
                        databaseEnvironment("jdbc:mysql://db.example.com:3306/auctiondb", "auction", "secret"),
                        config -> {
                            throw new SQLException("Table 'auctiondb.manual_bids' doesn't exist", "42S02", 1146);
                        }
                )
        );

        assertTrue(exception.getMessage().contains("Remote MySQL schema is incomplete"));
        assertTrue(exception.getMessage().contains("wallet"));
        assertTrue(exception.getMessage().contains("auth session"));
        assertTrue(exception.getMessage().contains("manual_bids"));
    }

    @Test
    void resolveDatabaseConfigTrimsUserButPreservesPassword() throws Exception {
        DatabaseConfig config = AuctionApiServerMain.resolveDatabaseConfig(
                databaseEnvironment("jdbc:mysql://db.example.com:3306/auctiondb", " auction ", " secret ")
        );

        Assertions.assertNotNull(config);
        assertEquals("jdbc:mysql://db.example.com:3306/auctiondb", config.jdbcUrl());
        assertEquals("auction", config.username());
        assertEquals(" secret ", config.password());
    }

    private static Map<String, String> databaseEnvironment(String jdbcUrl, String username, String password) {
        return Map.of(
                "AUCTION_DB_URL", jdbcUrl,
                "AUCTION_DB_USER", username,
                "AUCTION_DB_PASSWORD", password
        );
    }

    private static HttpServer invokeCreateServer(AuctionApiServerMain.PortSelection portSelection)
            throws Exception {

        Method createServer = AuctionApiServerMain.class.getDeclaredMethod(
                "createServer",
                AuctionApiServerMain.PortSelection.class
        );
        createServer.setAccessible(true);
        try {
            return (HttpServer) createServer.invoke(null, portSelection);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private static Connection h2(String name) throws SQLException {
        return DriverManager.getConnection(
                "jdbc:h2:mem:" + name + "_" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
                "sa",
                ""
        );
    }
}
