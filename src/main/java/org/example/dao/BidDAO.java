package org.example.dao;

import org.example.model.AutoBid;
import org.example.model.Bid;

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
import java.util.Optional;

public class BidDAO implements AutoCloseable {
    private final Connection conn;
    private final boolean ownsConnection;

    public BidDAO(String jdbcUrl, String username, String password) throws SQLException {
        DatabaseConfig.loadDriver();
        conn = DriverManager.getConnection(jdbcUrl, username, password);
        ownsConnection = true;
    }

    public BidDAO(Connection conn) {
        this.conn = Objects.requireNonNull(conn, "conn");
        this.ownsConnection = false;
    }

    public static BidDAO fromEnvironment() throws SQLException {
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        return new BidDAO(
                config.jdbcUrl(),
                config.username(),
                config.password()
        );
    }

    public void addBid(Bid bid) throws SQLException {
        String auctionId = bid.getItemId();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            if (originalAutoCommit) {
                conn.setAutoCommit(false);
            }

            markExistingWinningBidsOutbid(auctionId);

            String sql = """
                    INSERT INTO bids (
                        id,
                        auction_id,
                        bidder_id,
                        amount,
                        bid_source,
                        status,
                        placed_at
                    ) VALUES (?, ?, ?, ?, 'DESKTOP', 'WINNING', ?)
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, bid.getId());
                ps.setString(2, auctionId);
                ps.setString(3, bid.getBidderId());
                ps.setDouble(4, bid.getAmount());
                ps.setTimestamp(5, Timestamp.valueOf(bidTime(bid)));
                ps.executeUpdate();
            }

            updateAuctionWinner(auctionId, bid);
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

    public List<Bid> getBidsForItem(String itemId) throws SQLException {
        List<Bid> bids = new ArrayList<>();
        String sql = """
                SELECT id, bidder_id, amount, placed_at
                FROM bids
                WHERE auction_id = ?
                ORDER BY placed_at ASC, amount ASC
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String id = rs.getString("id");
                String bidderId = rs.getString("bidder_id");
                double amount = rs.getDouble("amount");
                Timestamp timestamp = rs.getTimestamp("placed_at");
                LocalDateTime bidTime = timestamp == null ? null : timestamp.toLocalDateTime();
                bids.add(new Bid(id, bidderId, itemId, amount, bidTime));
            }
        }
        return bids;
    }

    public void addOrUpdateAutoBid(AutoBid autoBid) throws SQLException {
        ensureSchema();
        String sql = """
                INSERT INTO auto_bids (bidder_id, auction_id, max_limit, bid_increment)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    max_limit = VALUES(max_limit),
                    bid_increment = VALUES(bid_increment)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, autoBid.getBidderId());
            ps.setString(2, autoBid.getItemId());
            ps.setDouble(3, autoBid.getMaxLimit());
            ps.setDouble(4, Math.max(0.0, autoBid.getBidIncrement()));
            ps.executeUpdate();
        }
    }

    public Optional<AutoBid> getAutoBid(String bidderId, String itemId) throws SQLException {
        ensureSchema();
        String sql = "SELECT id, max_limit, bid_increment FROM auto_bids WHERE bidder_id = ? AND auction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bidderId);
            ps.setString(2, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("id");
                double maxLimit = rs.getDouble("max_limit");
                double bidIncrement = rs.getDouble("bid_increment");
                return Optional.of(new AutoBid(id, bidderId, itemId, maxLimit, bidIncrement));
            }
        }
        return Optional.empty();
    }

    public List<AutoBid> getAllAutoBidsForItem(String itemId) throws SQLException {
        ensureSchema();
        List<AutoBid> autoBids = new ArrayList<>();
        String sql = "SELECT id, bidder_id, max_limit, bid_increment FROM auto_bids WHERE auction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String bidderId = rs.getString("bidder_id");
                double maxLimit = rs.getDouble("max_limit");
                double bidIncrement = rs.getDouble("bid_increment");
                autoBids.add(new AutoBid(id, bidderId, itemId, maxLimit, bidIncrement));
            }
        }
        return autoBids;
    }

    public void ensureSchema() throws SQLException {
        execute("""
                CREATE TABLE IF NOT EXISTS auto_bids (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    bidder_id VARCHAR(36) NOT NULL,
                    auction_id VARCHAR(36) NOT NULL,
                    max_limit DECIMAL(15, 2) NOT NULL,
                    bid_increment DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_auto_bids_bidder_auction (bidder_id, auction_id),
                    CONSTRAINT fk_auto_bids_bidder
                        FOREIGN KEY (bidder_id) REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT fk_auto_bids_auction
                        FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
                    CONSTRAINT chk_auto_bids_max_limit
                        CHECK (max_limit > 0),
                    CONSTRAINT chk_auto_bids_bid_increment
                        CHECK (bid_increment >= 0)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        if (!hasColumn("auto_bids", "bid_increment")) {
            execute("ALTER TABLE auto_bids ADD COLUMN bid_increment DECIMAL(15, 2) NOT NULL DEFAULT 0.00 AFTER max_limit");
        }
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute(sql);
        }
    }

    private boolean hasColumn(String tableName, String columnName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, tableName, columnName)) {
            return rs.next();
        }
    }

    private void markExistingWinningBidsOutbid(String auctionId) throws SQLException {
        String sql = "UPDATE bids SET status = 'OUTBID' WHERE auction_id = ? AND status = 'WINNING'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            ps.executeUpdate();
        }
    }

    private void updateAuctionWinner(String auctionId, Bid bid) throws SQLException {
        String sql = """
                UPDATE auctions
                SET current_price = ?,
                    winner_bidder_id = ?,
                    winning_bid_id = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, bid.getAmount());
            ps.setString(2, bid.getBidderId());
            ps.setString(3, bid.getId());
            ps.setString(4, auctionId);
            ps.executeUpdate();
        }
    }

    private LocalDateTime bidTime(Bid bid) {
        return bid.getBidTime() == null ? LocalDateTime.now() : bid.getBidTime();
    }

    @Override
    public void close() throws SQLException {
        if (ownsConnection && conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}
