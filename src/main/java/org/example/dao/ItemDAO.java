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

public class ItemDAO implements AutoCloseable {
    private final Connection conn;

    public ItemDAO(String jdbcUrl, String username, String password) throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(jdbcUrl, username, password);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Could not find JDBC driver", e);
        }
    }

    public static ItemDAO fromEnvironment() throws SQLException {
        return new ItemDAO(
                System.getenv("AUCTION_DB_URL"),
                System.getenv("AUCTION_DB_USER"),
                System.getenv("AUCTION_DB_PASSWORD")
        );
    }

    public void addItem(Item item, String type, String extraStr, int extraInt) throws SQLException {
        String sql = "INSERT INTO items (id, item_type, item_name, description, starting_price, current_price, seller_id, approval_status, start_time, end_time, extra_str, extra_int) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getId());
            ps.setString(2, type);
            ps.setString(3, item.getItemName());
            ps.setString(4, item.getDescription());
            ps.setDouble(5, item.getStartingPrice());
            ps.setDouble(6, item.getCurrentPrice());
            ps.setString(7, item.getSellerId());
            ps.setString(8, item.getApprovalStatus().name());
            ps.setTimestamp(9, item.getStartTime() != null ? Timestamp.valueOf(item.getStartTime()) : null);
            ps.setTimestamp(10, item.getEndTime() != null ? Timestamp.valueOf(item.getEndTime()) : null);
            ps.setString(11, extraStr);
            ps.setInt(12, extraInt);
            ps.executeUpdate();
        }
    }

    public void updateCurrentPrice(String itemId, double newPrice) throws SQLException {
        String sql = "UPDATE items SET current_price = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            ps.setString(2, itemId);
            ps.executeUpdate();
        }
    }

    public void updateApprovalStatus(String itemId, ApprovalStatus status) throws SQLException {
        String sql = "UPDATE items SET approval_status = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, itemId);
            ps.executeUpdate();
        }
    }

    public Item getItemById(String id) throws SQLException {
        String sql = "SELECT * FROM items WHERE id = ?";
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
        String sql = "SELECT * FROM items";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapItem(rs));
            }
        }
        return list;
    }

    private Item mapItem(ResultSet rs) throws SQLException {
        String type = rs.getString("item_type");
        String id = rs.getString("id");
        String name = rs.getString("item_name");
        String desc = rs.getString("description");
        double startingPrice = rs.getDouble("starting_price");
        double currentPrice = rs.getDouble("current_price");
        String sellerId = rs.getString("seller_id");
        String approvalStr = rs.getString("approval_status");
        Timestamp startTs = rs.getTimestamp("start_time");
        Timestamp endTs = rs.getTimestamp("end_time");
        String extraStr = rs.getString("extra_str");
        int extraInt = rs.getInt("extra_int");

        LocalDateTime startTime = startTs != null ? startTs.toLocalDateTime() : null;
        LocalDateTime endTime = endTs != null ? endTs.toLocalDateTime() : null;

        Item item = ItemFactory.createItem(type, id, name, desc, startingPrice, startTime, endTime, extraStr, extraInt);
        item.setCurrentPrice(currentPrice);
        item.setSellerId(sellerId);
        if (approvalStr != null) {
            item.setApprovalStatus(ApprovalStatus.valueOf(approvalStr));
        }

        return item;
    }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}
