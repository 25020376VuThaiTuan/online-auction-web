package dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import model.User;

public class UserDAO {
    private Connection conn;

    // Khởi tạo kết nối tới MySQL
    public UserDAO() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); // Driver MySQL
            conn = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/auctiondb?useSSL=false&serverTimezone=UTC",
                    "root",       // thay bằng user DB của bạn
                    "password"    // thay bằng mật khẩu DB của bạn
            );
        } catch (ClassNotFoundException e) {
            throw new SQLException("Không tìm thấy JDBC Driver", e);
        }
    }

    // Thêm user mới
    public void addUser(User user) throws SQLException {
        String sql = "INSERT INTO users(username, password, role) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getRole());
            ps.executeUpdate();
        }
    }

    // Lấy user theo id
    public User getUserById(int id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("role")
                );
            }
        }
        return null;
    }

    // Lấy toàn bộ user
    public List<User> getAllUsers() throws SQLException {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("role")
                ));
            }
        }
        return list;
    }

    // Cập nhật user
    public void updateUser(User user) throws SQLException {
        String sql = "UPDATE users SET username=?, password=?, role=? WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getRole());
            ps.setInt(4, user.getId());
            ps.executeUpdate();
        }
    }

    // Xóa user
    public void deleteUser(int id) throws SQLException {
        String sql = "DELETE FROM users WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
