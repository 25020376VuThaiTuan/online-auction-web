package org.example.dao;

import org.example.model.AutoBid;
import org.example.model.Bid;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BidDAO implements AutoCloseable {
    private final Connection conn;

    public BidDAO(String jdbcUrl, String username, String password) throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(jdbcUrl, username, password);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Could not find JDBC driver", e);
        }
    }

    public static BidDAO fromEnvironment() throws SQLException {
        return new BidDAO(
                System.getenv("AUCTION_DB_URL"),
                System.getenv("AUCTION_DB_USER"),
                System.getenv("AUCTION_DB_PASSWORD")
        );
    }

    public void addBid(Bid bid) throws SQLException {
        String sql = "INSERT INTO bids (id, bidder_id, item_id, amount, bid_time) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bid.getId());
            ps.setString(2, bid.getBidderId());
            ps.setString(3, bid.getItemId());
            ps.setDouble(4, bid.getAmount());
            ps.setTimestamp(5, Timestamp.valueOf(bid.getBidTime()));
            ps.executeUpdate();
        }
    }

    public List<Bid> getBidsForItem(String itemId) throws SQLException {
        List<Bid> bids = new ArrayList<>();
        String sql = "SELECT * FROM bids WHERE item_id = ? ORDER BY amount DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String id = rs.getString("id");
                String bidderId = rs.getString("bidder_id");
                double amount = rs.getDouble("amount");
                LocalDateTime bidTime = rs.getTimestamp("bid_time").toLocalDateTime();
                bids.add(new Bid(id, bidderId, itemId, amount, bidTime));
            }
        }
        return bids;
    }

    public void addOrUpdateAutoBid(AutoBid autoBid) throws SQLException {
        String sql = "INSERT INTO auto_bids (bidder_id, item_id, max_limit) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE max_limit = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, autoBid.getBidderId());
            ps.setString(2, autoBid.getItemId());
            ps.setDouble(3, autoBid.getMaxLimit());
            ps.setDouble(4, autoBid.getMaxLimit());
            ps.executeUpdate();
        }
    }

    public Optional<AutoBid> getAutoBid(String bidderId, String itemId) throws SQLException {
        String sql = "SELECT * FROM auto_bids WHERE bidder_id = ? AND item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bidderId);
            ps.setString(2, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("id");
                double maxLimit = rs.getDouble("max_limit");
                return Optional.of(new AutoBid(id, bidderId, itemId, maxLimit));
            }
        }
        return Optional.empty();
    }

    public List<AutoBid> getAllAutoBidsForItem(String itemId) throws SQLException {
        List<AutoBid> autoBids = new ArrayList<>();
        String sql = "SELECT * FROM auto_bids WHERE item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String bidderId = rs.getString("bidder_id");
                double maxLimit = rs.getDouble("max_limit");
                autoBids.add(new AutoBid(id, bidderId, itemId, maxLimit));
            }
        }
        return autoBids;
    }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}
