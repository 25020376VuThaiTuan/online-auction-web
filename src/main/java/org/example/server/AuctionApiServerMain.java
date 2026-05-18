package org.example.server;

import com.sun.net.httpserver.HttpServer;
import org.example.dao.BidDAO;
import org.example.dao.DatabaseConfig;
import org.example.dao.WalletDAO;
import org.example.service.AuthenticationService;
import org.example.service.AuctionWorkflowService;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AuctionApiServerMain {
    private static final int DEFAULT_PORT = 8081;
    private static final int DEFAULT_WORKER_THREADS = 16;
    private static final String DB_URL_ENV = "AUCTION_DB_URL";
    private static final String DB_USER_ENV = "AUCTION_DB_USER";
    private static final String DB_PASSWORD_ENV = "AUCTION_DB_PASSWORD";
    private static final List<RequiredTable> CORE_REQUIRED_DATABASE_SCHEMA = List.of(
            new RequiredTable("users", List.of("id", "username", "email", "password_hash", "role")),
            new RequiredTable("bidder_profiles", List.of("user_id", "wallet_balance")),
            new RequiredTable("seller_profiles", List.of("user_id", "store_name")),
            new RequiredTable("admin_profiles", List.of("user_id")),
            new RequiredTable("user_addresses", List.of("user_id", "address_label", "line_1")),
            new RequiredTable("categories", List.of("id", "slug", "name")),
            new RequiredTable("items", List.of("id", "seller_id", "category_id", "title", "description", "status")),
            new RequiredTable("auctions", List.of(
                    "id", "item_id", "seller_id", "starting_price", "current_price",
                    "start_at", "end_at", "status", "winner_bidder_id", "winning_bid_id"
            )),
            new RequiredTable("electronics_details", List.of("item_id", "brand", "warranty_months")),
            new RequiredTable("art_details", List.of("item_id", "artist", "year_created")),
            new RequiredTable("vehicle_details", List.of("item_id", "model_name", "mileage_km")),
            new RequiredTable("bids", List.of("id", "auction_id", "bidder_id", "amount", "bid_source", "status", "placed_at"))
    );
    private static final List<RequiredTable> SELF_HEALING_DATABASE_SCHEMA = List.of(
            new RequiredTable("auto_bids", List.of("id", "auction_id", "bidder_id", "max_limit", "bid_increment")),
            new RequiredTable("wallet_accounts", List.of("user_id", "balance", "pin_hash")),
            new RequiredTable("wallet_linked_accounts", List.of(
                    "id", "user_id", "account_name", "provider_name", "account_reference", "balance", "is_primary"
            )),
            new RequiredTable("wallet_holds", List.of("user_id", "hold_key", "reference_id", "amount"))
    );
    private static final List<String> MODERN_WALLET_TRANSACTION_COLUMNS = List.of(
            "id", "user_id", "reference_id", "transaction_type", "amount",
            "balance_before", "balance_after", "note", "created_at"
    );
    private static final List<String> LEGACY_WALLET_TRANSACTION_COLUMNS = List.of(
            "id", "bidder_id", "auction_id", "payment_id", "transaction_type",
            "amount", "balance_before", "balance_after", "note", "created_at"
    );

    private AuctionApiServerMain() {
    }

    public static void main(String[] args) throws IOException {
        runStartupPreflight();

        int port = resolvePort(args);
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        } catch (BindException exception) {
            throw new IOException(
                    "Port " + port + " is already in use. Stop the process using that port, "
                            + "or start this server with a different port using AUCTION_API_PORT, "
                            + "-Dauction.api.port, or --port.",
                    exception
            );
        }

        AuctionApiHandler apiHandler = new AuctionApiHandler(
                AuthenticationService.getInstance(),
                AuctionWorkflowService.getInstance(),
                new ApiSessionService(),
                new AuctionRealtimeBroker()
        );

        ExecutorService executor = Executors.newFixedThreadPool(resolveWorkerThreads());
        server.createContext("/api", apiHandler);
        server.setExecutor(executor);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            executor.shutdown();
        }));
        server.start();

        System.out.println("Auction API server is listening on http://0.0.0.0:" + port + "/api");
        System.out.println("Use POST /api/auth/login to get a token, then call the auction endpoints.");
    }

    static void runStartupPreflight() throws IOException {
        runStartupPreflight(
                System.getenv(),
                AuctionApiServerMain::verifyDatabaseConnection,
                AuctionApiServerMain::repairSupportedSchema
        );
    }

    static void runStartupPreflight(
            Map<String, String> environment,
            DatabaseConnectionVerifier connectionVerifier
    ) throws IOException {
        runStartupPreflight(environment, connectionVerifier, (config, cause) -> false);
    }

    static void runStartupPreflight(
            Map<String, String> environment,
            DatabaseConnectionVerifier connectionVerifier,
            DatabaseSchemaUpgrader schemaUpgrader
    ) throws IOException {
        DatabaseConfig databaseConfig = resolveDatabaseConfig(environment);
        if (databaseConfig == null) {
            System.out.println("No AUCTION_DB_* configuration detected; API server will use local demo data.");
            return;
        }

        try {
            connectionVerifier.verify(databaseConfig);
        } catch (SQLException exception) {
            SQLException failure = attemptSchemaRepair(databaseConfig, exception, connectionVerifier, schemaUpgrader);
            if (failure == null) {
                System.out.println("Database preflight succeeded for remote MySQL.");
                return;
            }
            throw databasePreflightFailure(remoteMysqlFailureMessage(failure), failure);
        }
        System.out.println("Database preflight succeeded for remote MySQL.");
    }

    static DatabaseConfig resolveDatabaseConfig(Map<String, String> environment) throws IOException {
        String jdbcUrl = envValue(environment, DB_URL_ENV);
        String username = envValue(environment, DB_USER_ENV);
        String password = envValue(environment, DB_PASSWORD_ENV);

        if (!hasText(jdbcUrl) && !hasText(username) && !hasText(password)) {
            return null;
        }
        if (!hasText(jdbcUrl) || !hasText(username) || !hasText(password)) {
            throw databasePreflightFailure(
                    "Database config is incomplete. Set AUCTION_DB_URL, AUCTION_DB_USER, and AUCTION_DB_PASSWORD separately."
            );
        }

        String problem = DatabaseConfig.validate(jdbcUrl, username, password);
        if (problem != null) {
            throw databasePreflightFailure(problem);
        }

        return new DatabaseConfig(jdbcUrl.trim(), username.trim(), password);
    }

    static String remoteMysqlFailureMessage(SQLException exception) {
        String sqlState = exception.getSQLState();
        int errorCode = exception.getErrorCode();
        String mysqlMessage = firstUsefulMessage(exception);

        if (containsIgnoreCase(mysqlMessage, "public key retrieval is not allowed")) {
            return "MySQL rejected authentication because public key retrieval is disabled. "
                    + "For a trusted remote MySQL server, add allowPublicKeyRetrieval=true to AUCTION_DB_URL, "
                    + "or configure SSL/key-based authentication. MySQL said: " + mysqlMessage;
        }
        if (errorCode == 1045 || "28000".equals(sqlState) || containsIgnoreCase(mysqlMessage, "access denied")) {
            return "MySQL authentication failed. Check AUCTION_DB_USER and AUCTION_DB_PASSWORD. "
                    + "MySQL said: " + mysqlMessage;
        }
        if (errorCode == 1049 || containsIgnoreCase(mysqlMessage, "unknown database")) {
            return "The database named in AUCTION_DB_URL does not exist or is not visible to this user. "
                    + "Create the schema or update AUCTION_DB_URL. MySQL said: " + mysqlMessage;
        }
        if (isSchemaSqlState(sqlState)
                || errorCode == 1054
                || containsIgnoreCase(mysqlMessage, "doesn't exist")
                || containsIgnoreCase(mysqlMessage, "unknown column")
                || containsIgnoreCase(mysqlMessage, "missing required table")
                || containsIgnoreCase(mysqlMessage, "missing required column")) {
            return "Remote MySQL schema is incomplete for the auction API. "
                    + "Apply schema.sql and migrations so bid history is stored in bids and auto-bids are stored in auto_bids. "
                    + "MySQL said: " + mysqlMessage;
        }
        if (isConnectionSqlState(sqlState)
                || containsIgnoreCase(mysqlMessage, "communications link failure")
                || containsIgnoreCase(mysqlMessage, "connection refused")
                || containsIgnoreCase(mysqlMessage, "connect timed out")
                || containsIgnoreCase(mysqlMessage, "no route to host")
                || containsIgnoreCase(mysqlMessage, "host is not allowed")) {
            return "Could not reach the remote MySQL server from AUCTION_DB_URL. "
                    + "Verify the host, port, firewall/security group, and that MySQL allows remote TCP connections. "
                    + "MySQL said: " + mysqlMessage;
        }

        return "Could not verify the remote MySQL connection from AUCTION_DB_URL. MySQL said: " + mysqlMessage;
    }

    static int resolvePort(String[] args) {
        String rawPort = resolvePortArgument(args);
        if (rawPort == null || rawPort.isBlank()) {
            rawPort = System.getProperty("auction.api.port");
        }
        if (rawPort == null || rawPort.isBlank()) {
            rawPort = System.getenv("AUCTION_API_PORT");
        }
        if (rawPort == null || rawPort.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(rawPort.trim());
            return port > 0 ? port : DEFAULT_PORT;
        } catch (NumberFormatException ignored) {
            return DEFAULT_PORT;
        }
    }

    static int resolveWorkerThreads() {
        String rawValue = System.getProperty("auction.api.workerThreads");
        if (rawValue == null || rawValue.isBlank()) {
            rawValue = System.getenv("AUCTION_API_WORKER_THREADS");
        }
        if (rawValue == null || rawValue.isBlank()) {
            return DEFAULT_WORKER_THREADS;
        }
        try {
            int workerThreads = Integer.parseInt(rawValue.trim());
            return workerThreads > 0 ? workerThreads : DEFAULT_WORKER_THREADS;
        } catch (NumberFormatException ignored) {
            return DEFAULT_WORKER_THREADS;
        }
    }

    private static String resolvePortArgument(String[] args) {
        if (args == null) {
            return null;
        }
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            if (arg == null || arg.isBlank()) {
                continue;
            }
            if (arg.startsWith("--port=")) {
                return arg.substring("--port=".length());
            }
            if ("--port".equals(arg) && index + 1 < args.length) {
                return args[index + 1];
            }
        }
        return null;
    }

    private static void verifyDatabaseConnection(DatabaseConfig databaseConfig) throws SQLException {
        try (Connection connection = databaseConfig.openConnection()) {
            if (!connection.isValid(2)) {
                throw new SQLException("MySQL connection opened but did not pass validation.");
            }
            verifyDatabaseSchema(connection);
        }
    }

    static void verifyDatabaseSchema(Connection connection) throws SQLException {
        verifyDatabaseSchema(connection, CORE_REQUIRED_DATABASE_SCHEMA);
        verifyDatabaseSchema(connection, SELF_HEALING_DATABASE_SCHEMA);
        verifyWalletTransactionSchema(connection);
    }

    static void verifyDatabaseSchema(Connection connection, List<RequiredTable> requiredTables) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        for (RequiredTable requiredTable : requiredTables) {
            if (!hasTable(metaData, catalog, requiredTable.name())) {
                throw new SQLException(
                        "Missing required table '" + requiredTable.name()
                                + "'. Apply schema.sql and migrations before starting the API server.",
                        "42S02",
                        1146
                );
            }
            for (String column : requiredTable.columns()) {
                if (!hasColumn(metaData, catalog, requiredTable.name(), column)) {
                    throw new SQLException(
                            "Missing required column '" + requiredTable.name() + "." + column
                                    + "'. Apply schema.sql and migrations before starting the API server.",
                            "42S22",
                            1054
                    );
                }
            }
        }
    }

    static void verifyWalletTransactionSchema(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        String tableName = "wallet_transactions";
        if (!hasTable(metaData, catalog, tableName)) {
            throw new SQLException(
                    "Missing required table 'wallet_transactions'. Apply schema.sql and migrations before starting the API server.",
                    "42S02",
                    1146
            );
        }

        List<String> actualColumns = listColumnNames(metaData, catalog, tableName);
        if (isWalletTransactionSchemaCompatible(actualColumns)) {
            return;
        }

        throw new SQLException(
                "wallet_transactions must include either modern columns "
                        + MODERN_WALLET_TRANSACTION_COLUMNS
                        + " or legacy columns "
                        + LEGACY_WALLET_TRANSACTION_COLUMNS
                        + ". Apply schema.sql and migrations before starting the API server.",
                "42S22",
                1054
        );
    }

    private static IOException databasePreflightFailure(String message) {
        return new IOException("Database preflight failed: " + message);
    }

    private static IOException databasePreflightFailure(String message, SQLException cause) {
        return new IOException("Database preflight failed: " + message, cause);
    }

    private static String envValue(Map<String, String> environment, String key) {
        if (environment == null) {
            return null;
        }
        return environment.get(key);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isConnectionSqlState(String sqlState) {
        return sqlState != null && sqlState.startsWith("08");
    }

    private static boolean isSchemaSqlState(String sqlState) {
        return "42S02".equals(sqlState) || "42S22".equals(sqlState);
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    static boolean isWalletTransactionSchemaCompatible(List<String> actualColumns) {
        return hasAllColumns(actualColumns, MODERN_WALLET_TRANSACTION_COLUMNS)
                || hasAllColumns(actualColumns, LEGACY_WALLET_TRANSACTION_COLUMNS);
    }

    private static String firstUsefulMessage(SQLException exception) {
        if (hasText(exception.getMessage())) {
            return exception.getMessage();
        }
        SQLException nextException = exception.getNextException();
        if (nextException != null && hasText(nextException.getMessage())) {
            return nextException.getMessage();
        }
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (hasText(cause.getMessage())) {
                return cause.getMessage();
            }
            cause = cause.getCause();
        }
        return exception.getClass().getSimpleName();
    }

    private static SQLException attemptSchemaRepair(
            DatabaseConfig databaseConfig,
            SQLException originalFailure,
            DatabaseConnectionVerifier connectionVerifier,
            DatabaseSchemaUpgrader schemaUpgrader
    ) {
        if (!isSchemaRepairCandidate(originalFailure) || schemaUpgrader == null) {
            return originalFailure;
        }

        try {
            if (!schemaUpgrader.upgrade(databaseConfig, originalFailure)) {
                return originalFailure;
            }
        } catch (SQLException repairFailure) {
            return repairFailure;
        }

        try {
            connectionVerifier.verify(databaseConfig);
            System.out.println("Applied bundled database schema upgrades to remote MySQL.");
            return null;
        } catch (SQLException verificationFailure) {
            return verificationFailure;
        }
    }

    private static boolean repairSupportedSchema(DatabaseConfig databaseConfig, SQLException ignored) throws SQLException {
        try (Connection connection = databaseConfig.openConnection()) {
            if (!connection.isValid(2)) {
                throw new SQLException("MySQL connection opened but did not pass validation.");
            }
            verifyDatabaseSchema(connection, CORE_REQUIRED_DATABASE_SCHEMA);
            try (BidDAO bidDAO = new BidDAO(connection);
                 WalletDAO walletDAO = new WalletDAO(connection)) {
                bidDAO.ensureSchema();
                walletDAO.ensureSchema();
            }
            return true;
        }
    }

    private static boolean isSchemaRepairCandidate(SQLException exception) {
        String sqlState = exception.getSQLState();
        int errorCode = exception.getErrorCode();
        String message = firstUsefulMessage(exception);
        return isSchemaSqlState(sqlState)
                || errorCode == 1054
                || errorCode == 1146
                || containsIgnoreCase(message, "doesn't exist")
                || containsIgnoreCase(message, "unknown column")
                || containsIgnoreCase(message, "missing required table")
                || containsIgnoreCase(message, "missing required column");
    }

    @FunctionalInterface
    interface DatabaseConnectionVerifier {
        void verify(DatabaseConfig databaseConfig) throws SQLException;
    }

    @FunctionalInterface
    interface DatabaseSchemaUpgrader {
        boolean upgrade(DatabaseConfig databaseConfig, SQLException cause) throws SQLException;
    }

    private static boolean hasTable(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        try (ResultSet resultSet = metaData.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                if (tableName.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> listColumnNames(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        List<String> columns = new java.util.ArrayList<>();
        try (ResultSet resultSet = metaData.getColumns(catalog, null, tableName, "%")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    private static boolean hasAllColumns(List<String> actualColumns, List<String> requiredColumns) {
        for (String requiredColumn : requiredColumns) {
            boolean found = false;
            for (String actualColumn : actualColumns) {
                if (requiredColumn.equalsIgnoreCase(actualColumn)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasColumn(
            DatabaseMetaData metaData,
            String catalog,
            String tableName,
            String columnName
    ) throws SQLException {
        try (ResultSet resultSet = metaData.getColumns(catalog, null, tableName, columnName)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getColumns(catalog, null, tableName, "%")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private record RequiredTable(String name, List<String> columns) {
    }
}
