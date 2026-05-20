package org.example.server;

import org.example.dao.DatabaseConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
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
}
