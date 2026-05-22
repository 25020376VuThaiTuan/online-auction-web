package org.example.dao;

import org.example.model.User;
import org.example.model.WalletLinkedAccount;
import org.example.model.WalletTransaction;
import org.example.util.CredentialHasher;
import org.example.util.MoneyUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class WalletDAO implements AutoCloseable {
    private final Connection conn;
    private final boolean ownsConnection;

    public WalletDAO(String jdbcUrl, String username, String password) throws SQLException {
        DatabaseConfig.loadDriver();
        conn = DriverManager.getConnection(jdbcUrl, username, password);
        ownsConnection = true;
    }

    public WalletDAO(Connection conn) {
        this.conn = Objects.requireNonNull(conn, "conn");
        this.ownsConnection = false;
    }

    public static WalletDAO fromEnvironment() throws SQLException {
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        return new WalletDAO(config.jdbcUrl(), config.username(), config.password());
    }

    public void ensureSchema() throws SQLException {
        execute("""
                CREATE TABLE IF NOT EXISTS wallet_accounts (
                    user_id VARCHAR(36) PRIMARY KEY,
                    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
                    pin_hash VARCHAR(255) NULL,
                    pin_recovery_code VARCHAR(255) NULL,
                    pin_recovery_expires_at DATETIME NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    CONSTRAINT fk_wallet_accounts_user
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT chk_wallet_accounts_balance
                        CHECK (balance >= 0)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        execute("ALTER TABLE wallet_accounts MODIFY pin_recovery_code VARCHAR(255) NULL");
        execute("""
                CREATE TABLE IF NOT EXISTS wallet_linked_accounts (
                    id VARCHAR(36) PRIMARY KEY,
                    user_id VARCHAR(36) NOT NULL,
                    account_name VARCHAR(150) NOT NULL,
                    provider_name VARCHAR(100) NOT NULL,
                    account_reference VARCHAR(100) NOT NULL,
                    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
                    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    CONSTRAINT fk_wallet_linked_accounts_user
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT chk_wallet_linked_accounts_balance
                        CHECK (balance >= 0)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE IF NOT EXISTS wallet_transactions (
                    id VARCHAR(36) PRIMARY KEY,
                    user_id VARCHAR(36) NOT NULL,
                    reference_id VARCHAR(36) NULL,
                    transaction_type ENUM(
                        'TOP_UP',
                        'WITHDRAWAL',
                        'BID_HOLD',
                        'BID_RELEASE',
                        'PAYMENT',
                        'REFUND',
                        'ADJUSTMENT',
                        'SELLER_PAYOUT',
                        'ADMIN_FEE',
                        'PIN_RESET'
                    ) NOT NULL,
                    amount DECIMAL(15, 2) NOT NULL,
                    balance_before DECIMAL(15, 2) NOT NULL,
                    balance_after DECIMAL(15, 2) NOT NULL,
                    note VARCHAR(255) NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_wallet_transactions_user
                        FOREIGN KEY (user_id) REFERENCES users(id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE IF NOT EXISTS wallet_holds (
                    user_id VARCHAR(36) NOT NULL,
                    hold_key VARCHAR(120) NOT NULL,
                    reference_id VARCHAR(36) NULL,
                    amount DECIMAL(15, 2) NOT NULL,
                    note VARCHAR(255) NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (user_id, hold_key),
                    CONSTRAINT fk_wallet_holds_user
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT chk_wallet_holds_amount
                        CHECK (amount >= 0)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        if (!hasColumn("wallet_linked_accounts", "balance")) {
            execute("ALTER TABLE wallet_linked_accounts ADD COLUMN balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00 AFTER account_reference");
        }
        execute("""
                ALTER TABLE wallet_transactions
                    MODIFY transaction_type ENUM(
                        'TOP_UP',
                        'WITHDRAWAL',
                        'BID_HOLD',
                        'BID_RELEASE',
                        'PAYMENT',
                        'REFUND',
                        'ADJUSTMENT',
                        'SELLER_PAYOUT',
                        'ADMIN_FEE',
                        'PIN_RESET'
                    ) NOT NULL
                """);
    }

    public void ensureWallet(User user, double initialBalance) throws SQLException {
        String sql = """
                INSERT INTO wallet_accounts (user_id, balance)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getId());
            ps.setBigDecimal(2, MoneyUtils.toDatabaseAmount(initialBalance));
            ps.executeUpdate();
        }
    }

    public Optional<Double> findBalance(String userId) throws SQLException {
        String sql = "SELECT balance FROM wallet_accounts WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(MoneyUtils.fromDatabaseAmount(rs.getBigDecimal("balance")));
            }
        }
        return Optional.empty();
    }

    public Optional<Double> findBalanceForUpdate(String userId) throws SQLException {
        String sql = "SELECT balance FROM wallet_accounts WHERE user_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(MoneyUtils.fromDatabaseAmount(rs.getBigDecimal("balance")));
            }
        }
        return Optional.empty();
    }

    public void updateBalance(String userId, double balance) throws SQLException {
        double safeBalance = Math.max(0.0, balance);
        String sql = "UPDATE wallet_accounts SET balance = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, MoneyUtils.toDatabaseAmount(safeBalance));
            ps.setString(2, userId);
            ps.executeUpdate();
        }

        String profileSql = """
                UPDATE bidder_profiles
                SET wallet_balance = ?, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(profileSql)) {
            ps.setBigDecimal(1, MoneyUtils.toDatabaseAmount(safeBalance));
            ps.setString(2, userId);
            ps.executeUpdate();
        }
    }

    public Optional<String> findPinHash(String userId) throws SQLException {
        String sql = "SELECT pin_hash FROM wallet_accounts WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.ofNullable(rs.getString("pin_hash"));
            }
        }
        return Optional.empty();
    }

    public void updatePinHash(String userId, String pinHash) throws SQLException {
        String sql = "UPDATE wallet_accounts SET pin_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pinHash);
            ps.setString(2, userId);
            ps.executeUpdate();
        }
    }

    public void saveRecoveryCode(String userId, String recoveryCode, LocalDateTime expiresAt) throws SQLException {
        String sql = """
                UPDATE wallet_accounts
                SET pin_recovery_code = ?,
                    pin_recovery_expires_at = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, recoveryCode);
            ps.setTimestamp(2, Timestamp.valueOf(expiresAt));
            ps.setString(3, userId);
            ps.executeUpdate();
        }
    }

    public boolean consumeRecoveryCode(String userId, String recoveryCode) throws SQLException {
        String sql = """
                SELECT pin_recovery_code, pin_recovery_expires_at
                FROM wallet_accounts
                WHERE user_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return false;
            }
            String storedCode = rs.getString("pin_recovery_code");
            Timestamp expiresAt = rs.getTimestamp("pin_recovery_expires_at");
            if (storedCode == null || expiresAt == null || !recoveryCodeAccepted(recoveryCode, storedCode)) {
                return false;
            }
            if (expiresAt.toLocalDateTime().isBefore(LocalDateTime.now())) {
                return false;
            }
        }

        String clearSql = """
                UPDATE wallet_accounts
                SET pin_recovery_code = NULL,
                    pin_recovery_expires_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(clearSql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }
        return true;
    }

    public void upsertHold(String userId, String holdKey, String referenceId, double amount, String note) throws SQLException {
        if (amount <= 0.0) {
            deleteHold(userId, holdKey);
            return;
        }
        String sql = """
                INSERT INTO wallet_holds (user_id, hold_key, reference_id, amount, note)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    reference_id = VALUES(reference_id),
                    amount = VALUES(amount),
                    note = VALUES(note),
                    updated_at = CURRENT_TIMESTAMP
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, holdKey);
            ps.setString(3, blankToNull(referenceId));
            ps.setBigDecimal(4, MoneyUtils.toDatabaseAmount(amount));
            ps.setString(5, note);
            ps.executeUpdate();
        }
    }

    public boolean deleteHold(String userId, String holdKey) throws SQLException {
        String sql = "DELETE FROM wallet_holds WHERE user_id = ? AND hold_key = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, holdKey);
            return ps.executeUpdate() > 0;
        }
    }

    public Map<String, Double> listHolds(String userId) throws SQLException {
        Map<String, Double> holds = new LinkedHashMap<>();
        String sql = """
                SELECT hold_key, amount
                FROM wallet_holds
                WHERE user_id = ?
                ORDER BY updated_at DESC, hold_key ASC
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                holds.put(rs.getString("hold_key"), MoneyUtils.fromDatabaseAmount(rs.getBigDecimal("amount")));
            }
        }
        return holds;
    }

    public void addTransaction(WalletTransaction transaction) throws SQLException {
        if (hasColumn("wallet_transactions", "user_id")) {
            String sql = """
                    INSERT INTO wallet_transactions (
                        id,
                        user_id,
                        reference_id,
                        transaction_type,
                        amount,
                        balance_before,
                        balance_after,
                        note,
                        created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, transaction.id());
                ps.setString(2, transaction.userId());
                ps.setString(3, blankToNull(transaction.referenceId()));
                ps.setString(4, transaction.transactionType());
                ps.setBigDecimal(5, MoneyUtils.toDatabaseAmount(transaction.amount()));
                ps.setBigDecimal(6, MoneyUtils.toDatabaseAmount(transaction.balanceBefore()));
                ps.setBigDecimal(7, MoneyUtils.toDatabaseAmount(transaction.balanceAfter()));
                ps.setString(8, transaction.note());
                ps.setTimestamp(9, Timestamp.valueOf(transaction.createdAt()));
                ps.executeUpdate();
            }
            return;
        }

        String sql = """
                INSERT INTO wallet_transactions (
                    bidder_id,
                    auction_id,
                    payment_id,
                    transaction_type,
                    amount,
                    balance_before,
                    balance_after,
                    note,
                    created_at
                ) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, transaction.userId());
            ps.setString(2, null);
            ps.setString(3, transaction.transactionType());
            ps.setBigDecimal(4, MoneyUtils.toDatabaseAmount(transaction.amount()));
            ps.setBigDecimal(5, MoneyUtils.toDatabaseAmount(transaction.balanceBefore()));
            ps.setBigDecimal(6, MoneyUtils.toDatabaseAmount(transaction.balanceAfter()));
            ps.setString(7, transaction.note());
            ps.setTimestamp(8, Timestamp.valueOf(transaction.createdAt()));
            ps.executeUpdate();
        }
    }

    public void addLinkedAccount(WalletLinkedAccount account) throws SQLException {
        if (account.primary()) {
            clearPrimaryLinkedAccounts(account.userId());
        }
        String sql = """
                INSERT INTO wallet_linked_accounts (
                    id,
                    user_id,
                    account_name,
                    provider_name,
                    account_reference,
                    balance,
                    is_primary,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, account.id());
            ps.setString(2, account.userId());
            ps.setString(3, account.accountName());
            ps.setString(4, account.providerName());
            ps.setString(5, account.accountReference());
            ps.setBigDecimal(6, MoneyUtils.toDatabaseAmount(account.balance()));
            ps.setBoolean(7, account.primary());
            ps.setTimestamp(8, Timestamp.valueOf(account.createdAt()));
            ps.executeUpdate();
        }
    }

    public List<WalletLinkedAccount> listLinkedAccounts(String userId) throws SQLException {
        List<WalletLinkedAccount> accounts = new ArrayList<>();
        String sql = """
                SELECT id, user_id, account_name, provider_name, account_reference, balance, is_primary, created_at
                FROM wallet_linked_accounts
                WHERE user_id = ?
                ORDER BY is_primary DESC, created_at DESC
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                accounts.add(new WalletLinkedAccount(
                        rs.getString("id"),
                        rs.getString("user_id"),
                        rs.getString("account_name"),
                        rs.getString("provider_name"),
                        rs.getString("account_reference"),
                        MoneyUtils.fromDatabaseAmount(rs.getBigDecimal("balance")),
                        rs.getBoolean("is_primary"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        }
        return accounts;
    }

    public Optional<Double> findLinkedAccountBalanceForUpdate(String userId, String accountId) throws SQLException {
        String sql = """
                SELECT balance
                FROM wallet_linked_accounts
                WHERE user_id = ? AND id = ?
                FOR UPDATE
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, accountId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(MoneyUtils.fromDatabaseAmount(rs.getBigDecimal("balance")));
            }
        }
        return Optional.empty();
    }

    public void updateLinkedAccountBalance(String userId, String accountId, double balance) throws SQLException {
        String sql = """
                UPDATE wallet_linked_accounts
                SET balance = ?, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, MoneyUtils.toDatabaseAmount(balance));
            ps.setString(2, userId);
            ps.setString(3, accountId);
            ps.executeUpdate();
        }
    }

    public void setPrimaryLinkedAccount(String userId, String accountId) throws SQLException {
        clearPrimaryLinkedAccounts(userId);
        String sql = """
                UPDATE wallet_linked_accounts
                SET is_primary = TRUE, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, accountId);
            ps.executeUpdate();
        }
    }

    public boolean deleteLinkedAccount(String userId, String accountId) throws SQLException {
        String sql = "DELETE FROM wallet_linked_accounts WHERE user_id = ? AND id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, accountId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<WalletTransaction> listTransactions(String userId) throws SQLException {
        List<WalletTransaction> transactions = new ArrayList<>();
        String sql = transactionSelectSql("WHERE " + transactionUserColumn() + " = ?");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                transactions.add(new WalletTransaction(
                        rs.getString("id"),
                        rs.getString("user_id"),
                        rs.getString("transaction_type"),
                        MoneyUtils.fromDatabaseAmount(rs.getBigDecimal("amount")),
                        MoneyUtils.fromDatabaseAmount(rs.getBigDecimal("balance_before")),
                        MoneyUtils.fromDatabaseAmount(rs.getBigDecimal("balance_after")),
                        rs.getString("reference_id"),
                        rs.getString("note"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        }
        return transactions;
    }

    public List<WalletTransaction> listAllTransactions() throws SQLException {
        List<WalletTransaction> transactions = new ArrayList<>();
        String sql = transactionSelectSql("");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                transactions.add(new WalletTransaction(
                        rs.getString("id"),
                        rs.getString("user_id"),
                        rs.getString("transaction_type"),
                        MoneyUtils.fromDatabaseAmount(rs.getBigDecimal("amount")),
                        MoneyUtils.fromDatabaseAmount(rs.getBigDecimal("balance_before")),
                        MoneyUtils.fromDatabaseAmount(rs.getBigDecimal("balance_after")),
                        rs.getString("reference_id"),
                        rs.getString("note"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        }
        return transactions;
    }

    private String transactionSelectSql(String whereClause) throws SQLException {
        if (hasColumn("wallet_transactions", "user_id")) {
            return """
                    SELECT id, user_id, reference_id, transaction_type, amount,
                           balance_before, balance_after, note, created_at
                    FROM wallet_transactions
                    %s
                    ORDER BY created_at DESC, id DESC
                    """.formatted(whereClause);
        }
        return """
                SELECT CAST(id AS CHAR) AS id,
                       bidder_id AS user_id,
                       auction_id AS reference_id,
                       transaction_type,
                       amount,
                       balance_before,
                       balance_after,
                       note,
                       created_at
                FROM wallet_transactions
                %s
                ORDER BY created_at DESC, id DESC
                """.formatted(whereClause);
    }

    private String transactionUserColumn() throws SQLException {
        return hasColumn("wallet_transactions", "user_id") ? "user_id" : "bidder_id";
    }

    private boolean hasColumn(String tableName, String columnName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, tableName, columnName)) {
            return rs.next();
        }
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute(sql);
        }
    }

    private void clearPrimaryLinkedAccounts(String userId) throws SQLException {
        String sql = """
                UPDATE wallet_linked_accounts
                SET is_primary = FALSE, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean recoveryCodeAccepted(String recoveryCode, String storedCode) {
        if (recoveryCode == null || recoveryCode.isBlank() || storedCode == null || storedCode.isBlank()) {
            return false;
        }
        return CredentialHasher.isHashed(storedCode)
                ? CredentialHasher.verify(recoveryCode, storedCode)
                : recoveryCode.equals(storedCode);
    }

    @Override
    public void close() throws SQLException {
        if (ownsConnection && conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}
