package org.example.dao;

import org.example.model.Admin;
import org.example.model.Bidder;
import org.example.model.Seller;
import org.example.model.User;
import org.example.util.CredentialHasher;
import org.example.util.MoneyUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class UserDAO implements AutoCloseable {
    private static final String USER_SELECT = """
            SELECT u.id,
                   u.username,
                   u.email,
                   u.password_hash,
                   u.role,
                   u.full_name,
                   u.phone,
                   u.avatar_url,
                   bp.wallet_balance,
                   ua.line_1 AS profile_address
            FROM users u
            LEFT JOIN bidder_profiles bp ON bp.user_id = u.id
            LEFT JOIN user_addresses ua
                ON ua.user_id = u.id AND ua.address_label = 'PROFILE'
            """;

    private final Connection conn;
    private final boolean ownsConnection;

    public UserDAO(String jdbcUrl, String username, String password) throws SQLException {
        conn = new DatabaseConfig(jdbcUrl, username, password).openConnection();
        ownsConnection = true;
    }

    public UserDAO(Connection conn) {
        this.conn = Objects.requireNonNull(conn, "conn");
        this.ownsConnection = false;
    }

    public static UserDAO fromEnvironment() throws SQLException {
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        return new UserDAO(
                config.jdbcUrl(),
                config.username(),
                config.password()
        );
    }

    public void addUser(User user) throws SQLException {
        upsertUser(user);
    }

    public Optional<User> findByUsername(String username) throws SQLException {
        String sql = USER_SELECT + " WHERE LOWER(u.username) = LOWER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapUser(rs));
            }
        }
        return Optional.empty();
    }

    public Optional<User> findByEmail(String email) throws SQLException {
        String sql = USER_SELECT + " WHERE LOWER(u.email) = LOWER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapUser(rs));
            }
        }
        return Optional.empty();
    }

    public User getUserById(String id) throws SQLException {
        String sql = USER_SELECT + " WHERE u.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapUser(rs);
            }
        }
        return null;
    }

    public List<User> getAllUsers() throws SQLException {
        List<User> list = new ArrayList<>();
        String sql = USER_SELECT + " ORDER BY u.created_at DESC, u.username ASC";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapUser(rs));
            }
        }
        return list;
    }

    public void updateUser(User user) throws SQLException {
        upsertUser(user);
    }

    public void recordLogin(String userId) throws SQLException {
        if (isBlank(userId)) {
            return;
        }

        String sql = "UPDATE users SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }
    }

    public void deleteUser(String id) throws SQLException {
        String sql = "DELETE FROM users WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    private void upsertUser(User user) throws SQLException {
        if (user == null) {
            return;
        }

        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            if (originalAutoCommit) {
                conn.setAutoCommit(false);
            }
            UserTarget userTarget = resolveUserTarget(user);
            if (userTarget.insert()) {
                insertUserRow(user, userTarget.userId());
            } else {
                updateUserRow(user, userTarget.userId());
            }
            saveRoleProfile(user, userTarget.userId());
            saveProfileAddress(user, userTarget.userId());
            if (originalAutoCommit) {
                conn.commit();
            }
        } catch (SQLException e) {
            if (originalAutoCommit) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (originalAutoCommit) {
                conn.setAutoCommit(true);
            }
        }
    }

    private void insertUserRow(User user, String userId) throws SQLException {
        String sql = """
                INSERT INTO users (
                    id,
                    username,
                    email,
                    password_hash,
                    role,
                    full_name,
                    phone,
                    avatar_url
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, user.getUsername());
            ps.setString(3, safeEmail(user));
            ps.setString(4, persistedPasswordHash(user));
            ps.setString(5, safeRole(user.getRole()));
            ps.setString(6, emptyToNull(user.getFullName()));
            ps.setString(7, emptyToNull(user.getPhoneNumber()));
            ps.setString(8, emptyToNull(user.getAvatarUrl()));
            ps.executeUpdate();
        }
    }

    private void updateUserRow(User user, String userId) throws SQLException {
        String sql = """
                UPDATE users
                SET username = ?,
                    email = ?,
                    password_hash = ?,
                    role = ?,
                    full_name = ?,
                    phone = ?,
                    avatar_url = ?
                WHERE id = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, safeEmail(user));
            ps.setString(3, persistedPasswordHash(user));
            ps.setString(4, safeRole(user.getRole()));
            ps.setString(5, emptyToNull(user.getFullName()));
            ps.setString(6, emptyToNull(user.getPhoneNumber()));
            ps.setString(7, emptyToNull(user.getAvatarUrl()));
            ps.setString(8, userId);
            ps.executeUpdate();
        }
    }

    private UserTarget resolveUserTarget(User user) throws SQLException {
        Optional<String> existingById = findUserId("id = ?", user.getId());
        Optional<String> existingByUsername = findUserId("LOWER(username) = LOWER(?)", user.getUsername());
        Optional<String> existingByEmail = findUserId("LOWER(email) = LOWER(?)", safeEmail(user));

        if (existingById.isPresent() && existingByUsername.isPresent()
                && !existingById.get().equals(existingByUsername.get())) {
            throw duplicateValue("Username is already registered.");
        }

        String persistentUserId = existingById.or(() -> existingByUsername).orElse(user.getId());
        boolean insert = existingById.isEmpty() && existingByUsername.isEmpty();
        if (existingByEmail.isPresent() && !existingByEmail.get().equals(persistentUserId)) {
            throw duplicateValue("Email address is already registered.");
        }

        return new UserTarget(persistentUserId, insert);
    }

    private Optional<String> findUserId(String whereClause, String value) throws SQLException {
        if (isBlank(value)) {
            return Optional.empty();
        }

        String sql = "SELECT id FROM users WHERE " + whereClause + " LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value.trim());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(rs.getString("id"));
            }
        }
        return Optional.empty();
    }

    private void saveRoleProfile(User user, String userId) throws SQLException {
        String role = safeRole(user.getRole());
        switch (role) {
            case "ADMIN" -> {
                String sql = "INSERT IGNORE INTO admin_profiles (user_id) VALUES (?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, userId);
                    ps.executeUpdate();
                }
            }
            case "SELLER" -> {
                String sql = """
                        INSERT INTO seller_profiles (user_id, store_name)
                        VALUES (?, ?)
                        ON DUPLICATE KEY UPDATE store_name = VALUES(store_name)
                        """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, userId);
                    ps.setString(2, valueOrDefault(user.getFullName(), user.getUsername()));
                    ps.executeUpdate();
                }
            }
            default -> {
                double balance = user instanceof Bidder bidder ? bidder.getBalance() : 0.0;
                String sql = """
                        INSERT INTO bidder_profiles (user_id, wallet_balance)
                        VALUES (?, ?)
                        ON DUPLICATE KEY UPDATE wallet_balance = VALUES(wallet_balance)
                        """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, userId);
                    ps.setBigDecimal(2, MoneyUtils.toDatabaseAmount(balance));
                    ps.executeUpdate();
                }
            }
        }
    }

    private void saveProfileAddress(User user, String userId) throws SQLException {
        String deleteSql = "DELETE FROM user_addresses WHERE user_id = ? AND address_label = 'PROFILE'";
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }

        if (isBlank(user.getAddress())) {
            return;
        }

        String insertSql = """
                INSERT INTO user_addresses (
                    user_id,
                    address_label,
                    contact_name,
                    line_1,
                    city,
                    country_code,
                    is_default
                ) VALUES (?, 'PROFILE', ?, ?, 'N/A', 'VN', TRUE)
                """;
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, userId);
            ps.setString(2, valueOrDefault(user.getFullName(), user.getUsername()));
            ps.setString(3, user.getAddress());
            ps.executeUpdate();
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        String role = safeRole(rs.getString("role"));
        String id = rs.getString("id");
        String username = rs.getString("username");
        String password = rs.getString("password_hash");
        String email = rs.getString("email");

        User user = switch (role) {
            case "ADMIN" -> new Admin(id, username, password, email);
            case "SELLER" -> new Seller(id, username, password, email);
            default -> new Bidder(id, username, password, email, MoneyUtils.fromDatabaseAmount(rs.getBigDecimal("wallet_balance")));
        };
        user.setRole(role);
        user.setFullName(rs.getString("full_name"));
        user.setPhoneNumber(rs.getString("phone"));
        user.setAvatarUrl(rs.getString("avatar_url"));
        user.setAddress(rs.getString("profile_address"));
        return user;
    }

    private String safeEmail(User user) {
        if (!isBlank(user.getEmail())) {
            return user.getEmail().trim();
        }
        return user.getUsername() + "@local";
    }

    private String persistedPasswordHash(User user) {
        String credential = user == null ? null : user.getPasswordHash();
        if (CredentialHasher.isHashed(credential)) {
            return credential;
        }
        if (isBlank(credential)) {
            throw new IllegalArgumentException("User password hash is required.");
        }
        return CredentialHasher.hash(credential);
    }

    private String safeRole(String role) {
        return isBlank(role) ? "BIDDER" : role.trim().toUpperCase();
    }

    private String valueOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String emptyToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private SQLIntegrityConstraintViolationException duplicateValue(String message) {
        return new SQLIntegrityConstraintViolationException(message);
    }

    private record UserTarget(String userId, boolean insert) {
    }

    @Override
    public void close() throws SQLException {
        if (ownsConnection && conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}
