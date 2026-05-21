package org.example.dao;

import org.example.model.ApprovalStatus;
import org.example.model.Item;
import org.example.model.ItemFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ItemDAO implements AutoCloseable {
    private static final String ITEM_SELECT = """
            SELECT i.id,
                   i.seller_id,
                   i.title,
                   i.description,
                   i.status AS item_status,
                   c.slug AS category_slug,
                   a.starting_price,
                   a.current_price,
                   a.start_at,
                   a.end_at,
                   ed.brand AS electronics_brand,
                   ed.warranty_months,
                   ad.artist,
                   ad.year_created,
                   vd.model_name AS vehicle_model,
                   vd.mileage_km
            FROM items i
            JOIN categories c ON c.id = i.category_id
            JOIN auctions a ON a.item_id = i.id
            LEFT JOIN electronics_details ed ON ed.item_id = i.id
            LEFT JOIN art_details ad ON ad.item_id = i.id
            LEFT JOIN vehicle_details vd ON vd.item_id = i.id
            """;

    private final Connection conn;
    private final boolean ownsConnection;

    public ItemDAO(String jdbcUrl, String username, String password) throws SQLException {
        DatabaseConfig.loadDriver();
        conn = DriverManager.getConnection(jdbcUrl, username, password);
        ownsConnection = true;
    }

    public ItemDAO(Connection conn) {
        this.conn = Objects.requireNonNull(conn, "conn");
        this.ownsConnection = false;
    }

    public static ItemDAO fromEnvironment() throws SQLException {
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        return new ItemDAO(
                config.jdbcUrl(),
                config.username(),
                config.password()
        );
    }

    public void addItem(Item item, String type, String extraStr, int extraInt) throws SQLException {
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            if (originalAutoCommit) {
                conn.setAutoCommit(false);
            }

            String safeType = normalizeType(type);
            long categoryId = resolveCategoryId(safeType);

            String itemSql = """
                    INSERT INTO items (
                        id,
                        seller_id,
                        category_id,
                        title,
                        description,
                        item_condition,
                        status
                    ) VALUES (?, ?, ?, ?, ?, 'USED', ?)
                    ON DUPLICATE KEY UPDATE
                        seller_id = VALUES(seller_id),
                        category_id = VALUES(category_id),
                        title = VALUES(title),
                        description = VALUES(description),
                        status = VALUES(status)
                    """;
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                ps.setString(1, item.getId());
                ps.setString(2, item.getSellerId());
                ps.setLong(3, categoryId);
                ps.setString(4, item.getItemName());
                ps.setString(5, item.getDescription());
                ps.setString(6, toItemStatus(item.getApprovalStatus()));
                ps.executeUpdate();
            }

            upsertDetails(item.getId(), safeType, extraStr, extraInt);
            upsertAuction(item);

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

    public void updateCurrentPrice(String itemId, double newPrice) throws SQLException {
        String sql = "UPDATE auctions SET current_price = ?, updated_at = CURRENT_TIMESTAMP WHERE item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            ps.setString(2, itemId);
            ps.executeUpdate();
        }
    }

    public void updateAuctionProgress(String itemId, double currentPrice, LocalDateTime endTime, String status) throws SQLException {
        String sql = """
                UPDATE auctions
                SET current_price = ?,
                    end_at = ?,
                    status = ?,
                    closed_at = CASE WHEN ? = 'FINISHED' THEN CURRENT_TIMESTAMP ELSE closed_at END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE item_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, currentPrice);
            ps.setTimestamp(2, timestamp(endTime));
            ps.setString(3, status);
            ps.setString(4, status);
            ps.setString(5, itemId);
            ps.executeUpdate();
        }
    }

    public void lockAuctionForUpdate(String itemId) throws SQLException {
        String sql = "SELECT id FROM auctions WHERE item_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Auction row not found for item: " + itemId, "42S02", 1146);
                }
            }
        }
    }

    public void updateAuctionWindow(String itemId, LocalDateTime startTime, LocalDateTime endTime, String status) throws SQLException {
        String sql = """
                UPDATE auctions
                SET start_at = ?,
                    end_at = ?,
                    status = ?,
                    closed_at = CASE WHEN ? = 'FINISHED' THEN CURRENT_TIMESTAMP ELSE closed_at END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE item_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, timestamp(startTime));
            ps.setTimestamp(2, timestamp(endTime));
            ps.setString(3, status);
            ps.setString(4, status);
            ps.setString(5, itemId);
            ps.executeUpdate();
        }
    }

    public void updateApprovalStatus(String itemId, ApprovalStatus status) throws SQLException {
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            if (originalAutoCommit) {
                conn.setAutoCommit(false);
            }

            String itemSql = "UPDATE items SET status = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                ps.setString(1, toItemStatus(status));
                ps.setString(2, itemId);
                ps.executeUpdate();
            }

            String auctionSql = "UPDATE auctions SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE item_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(auctionSql)) {
                ps.setString(1, toAuctionStatus(status));
                ps.setString(2, itemId);
                ps.executeUpdate();
            }

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

    public Item getItemById(String id) throws SQLException {
        String sql = ITEM_SELECT + " WHERE i.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapItem(rs);
            }
        }
        return null;
    }

    public List<Item> getAllItems() throws SQLException {
        List<Item> list = new ArrayList<>();
        String sql = ITEM_SELECT + " ORDER BY a.start_at ASC, i.created_at ASC";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapItem(rs));
            }
        }
        return list;
    }

    private void upsertAuction(Item item) throws SQLException {
        String sql = """
                INSERT INTO auctions (
                    id,
                    item_id,
                    seller_id,
                    starting_price,
                    current_price,
                    minimum_increment,
                    start_at,
                    end_at,
                    status
                ) VALUES (?, ?, ?, ?, ?, 1.00, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    seller_id = VALUES(seller_id),
                    starting_price = VALUES(starting_price),
                    current_price = VALUES(current_price),
                    start_at = VALUES(start_at),
                    end_at = VALUES(end_at),
                    status = VALUES(status)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getId());
            ps.setString(2, item.getId());
            ps.setString(3, item.getSellerId());
            ps.setDouble(4, item.getStartingPrice());
            ps.setDouble(5, item.getCurrentPrice());
            ps.setTimestamp(6, timestamp(item.getStartTime()));
            ps.setTimestamp(7, timestamp(item.getEndTime()));
            ps.setString(8, toAuctionStatus(item.getApprovalStatus()));
            ps.executeUpdate();
        }
    }

    private void upsertDetails(String itemId, String type, String extraStr, int extraInt) throws SQLException {
        String safeText = isBlank(extraStr) ? "Unknown" : extraStr.trim();
        switch (type) {
            case "electronics" -> {
                String sql = """
                        INSERT INTO electronics_details (item_id, brand, warranty_months)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            brand = VALUES(brand),
                            warranty_months = VALUES(warranty_months)
                        """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, itemId);
                    ps.setString(2, safeText);
                    ps.setInt(3, Math.max(0, extraInt));
                    ps.executeUpdate();
                }
            }
            case "art" -> {
                String sql = """
                        INSERT INTO art_details (item_id, artist, year_created)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            artist = VALUES(artist),
                            year_created = VALUES(year_created)
                        """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, itemId);
                    ps.setString(2, safeText);
                    ps.setInt(3, extraInt);
                    ps.executeUpdate();
                }
            }
            case "vehicle" -> {
                String sql = """
                        INSERT INTO vehicle_details (item_id, manufacturer, model_name, mileage_km)
                        VALUES (?, 'Vehicle', ?, ?)
                        ON DUPLICATE KEY UPDATE
                            model_name = VALUES(model_name),
                            mileage_km = VALUES(mileage_km)
                        """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, itemId);
                    ps.setString(2, safeText);
                    ps.setInt(3, Math.max(0, extraInt));
                    ps.executeUpdate();
                }
            }
            default -> {
            }
        }
    }

    private long resolveCategoryId(String type) throws SQLException {
        String findSql = "SELECT id FROM categories WHERE LOWER(slug) = LOWER(?) OR LOWER(name) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setString(1, type);
            ps.setString(2, type);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        }

        String insertSql = "INSERT INTO categories (slug, name, description) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type);
            ps.setString(2, displayName(type));
            ps.setString(3, displayName(type) + " items");
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new SQLException("Could not create category for type: " + type);
    }

    private Item mapItem(ResultSet rs) throws SQLException {
        String type = normalizeType(rs.getString("category_slug"));
        String id = rs.getString("id");
        String name = rs.getString("title");
        String desc = rs.getString("description");
        double startingPrice = rs.getDouble("starting_price");
        double currentPrice = rs.getDouble("current_price");
        String sellerId = rs.getString("seller_id");
        Timestamp startTs = rs.getTimestamp("start_at");
        Timestamp endTs = rs.getTimestamp("end_at");

        String extraStr = switch (type) {
            case "electronics" -> rs.getString("electronics_brand");
            case "art" -> rs.getString("artist");
            case "vehicle" -> rs.getString("vehicle_model");
            default -> "";
        };
        int extraInt = switch (type) {
            case "electronics" -> rs.getInt("warranty_months");
            case "art" -> rs.getInt("year_created");
            case "vehicle" -> rs.getInt("mileage_km");
            default -> 0;
        };

        LocalDateTime startTime = startTs != null ? startTs.toLocalDateTime() : null;
        LocalDateTime endTime = endTs != null ? endTs.toLocalDateTime() : null;

        Item item = ItemFactory.createItem(type, id, name, desc, startingPrice, startTime, endTime, extraStr, extraInt);
        item.setCurrentPrice(currentPrice);
        item.setSellerId(sellerId);
        item.setApprovalStatus(fromItemStatus(rs.getString("item_status")));
        return item;
    }

    private String normalizeType(String type) {
        if (isBlank(type)) {
            return "electronics";
        }
        String normalized = type.trim().toLowerCase();
        return switch (normalized) {
            case "electronics", "art", "vehicle" -> normalized;
            default -> "electronics";
        };
    }

    private String displayName(String type) {
        return type.substring(0, 1).toUpperCase() + type.substring(1).toLowerCase();
    }

    private String toItemStatus(ApprovalStatus status) {
        return switch (status == null ? ApprovalStatus.PENDING : status) {
            case PENDING -> "PENDING_APPROVAL";
            case APPROVED -> "READY";
            case REJECTED -> "ARCHIVED";
        };
    }

    private String toAuctionStatus(ApprovalStatus status) {
        return switch (status == null ? ApprovalStatus.PENDING : status) {
            case PENDING -> "DRAFT";
            case APPROVED -> "OPEN";
            case REJECTED -> "CANCELLED";
        };
    }

    private ApprovalStatus fromItemStatus(String status) {
        if (status == null) {
            return ApprovalStatus.PENDING;
        }
        return switch (status.toUpperCase()) {
            case "READY" -> ApprovalStatus.APPROVED;
            case "ARCHIVED" -> ApprovalStatus.REJECTED;
            default -> ApprovalStatus.PENDING;
        };
    }

    private Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public void close() throws SQLException {
        if (ownsConnection && conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}
