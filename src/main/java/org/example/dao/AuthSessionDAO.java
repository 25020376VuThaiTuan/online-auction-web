package org.example.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class AuthSessionDAO implements AutoCloseable {
    private final Connection conn;
    private final boolean ownsConnection;

    public AuthSessionDAO(String jdbcUrl, String username, String password) throws SQLException {
        DatabaseConfig.loadDriver();
        conn = DriverManager.getConnection(jdbcUrl, username, password);
        ownsConnection = true;
    }

    public AuthSessionDAO(Connection conn) {
        this.conn = Objects.requireNonNull(conn, "conn");
        this.ownsConnection = false;
    }

    public static AuthSessionDAO fromEnvironment() throws SQLException {
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        return new AuthSessionDAO(config.jdbcUrl(), config.username(), config.password());
    }

    public void ensureSchema() throws SQLException {
        execute("""
                CREATE TABLE IF NOT EXISTS auth_sessions (
                    id VARCHAR(36) PRIMARY KEY,
                    user_id VARCHAR(36) NOT NULL,
                    refresh_token_hash VARCHAR(255) NOT NULL,
                    user_agent VARCHAR(255) NULL,
                    ip_address VARCHAR(45) NULL,
                    expires_at DATETIME NOT NULL,
                    revoked_at DATETIME NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_auth_sessions_user
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        if (!hasColumn("auth_sessions", "refresh_token_hash")) {
            execute("ALTER TABLE auth_sessions ADD COLUMN refresh_token_hash VARCHAR(255) NOT NULL AFTER user_id");
        }
        if (!hasColumn("auth_sessions", "user_agent")) {
            execute("ALTER TABLE auth_sessions ADD COLUMN user_agent VARCHAR(255) NULL AFTER refresh_token_hash");
        }
        if (!hasColumn("auth_sessions", "ip_address")) {
            execute("ALTER TABLE auth_sessions ADD COLUMN ip_address VARCHAR(45) NULL AFTER user_agent");
        }
        if (!hasColumn("auth_sessions", "revoked_at")) {
            execute("ALTER TABLE auth_sessions ADD COLUMN revoked_at DATETIME NULL AFTER expires_at");
        }
        if (!hasColumn("auth_sessions", "created_at")) {
            execute("ALTER TABLE auth_sessions ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER revoked_at");
        }
        if (!hasIndex("auth_sessions", "idx_auth_sessions_token_hash")) {
            execute("CREATE INDEX idx_auth_sessions_token_hash ON auth_sessions (refresh_token_hash)");
        }
    }

    public void createSession(String sessionId, String userId, String tokenHash, Instant createdAt, Instant expiresAt) throws SQLException {
        String sql = """
                INSERT INTO auth_sessions (
                    id,
                    user_id,
                    refresh_token_hash,
                    user_agent,
                    ip_address,
                    expires_at,
                    revoked_at,
                    created_at
                ) VALUES (?, ?, ?, NULL, NULL, ?, NULL, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, userId);
            ps.setString(3, tokenHash);
            ps.setTimestamp(4, Timestamp.from(expiresAt));
            ps.setTimestamp(5, Timestamp.from(createdAt));
            ps.executeUpdate();
        }
    }

    public Optional<PersistedSession> findActiveSessionByTokenHash(String tokenHash) throws SQLException {
        String sql = """
                SELECT id, user_id, created_at, expires_at
                FROM auth_sessions
                WHERE refresh_token_hash = ?
                  AND revoked_at IS NULL
                ORDER BY created_at DESC
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(new PersistedSession(
                        rs.getString("id"),
                        rs.getString("user_id"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("expires_at").toInstant()
                ));
            }
        }
        return Optional.empty();
    }

    public void revokeByTokenHash(String tokenHash) throws SQLException {
        String sql = """
                UPDATE auth_sessions
                SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP)
                WHERE refresh_token_hash = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            ps.executeUpdate();
        }
    }

    public void deleteExpiredSessions() throws SQLException {
        String sql = """
                DELETE FROM auth_sessions
                WHERE expires_at < CURRENT_TIMESTAMP
                   OR revoked_at IS NOT NULL
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    private boolean hasColumn(String tableName, String columnName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, tableName, columnName)) {
            return rs.next();
        }
    }

    private boolean hasIndex(String tableName, String indexName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getIndexInfo(conn.getCatalog(), null, tableName, false, false)) {
            while (rs.next()) {
                String candidate = rs.getString("INDEX_NAME");
                if (indexName.equalsIgnoreCase(candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute(sql);
        }
    }

    @Override
    public void close() throws SQLException {
        if (ownsConnection && conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    public record PersistedSession(
            String sessionId,
            String userId,
            Instant createdAt,
            Instant expiresAt
    ) {
    }
}
