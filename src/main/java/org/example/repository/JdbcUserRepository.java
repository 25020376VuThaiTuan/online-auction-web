package org.example.repository;

import org.example.dao.UserDAO;
import org.example.model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class JdbcUserRepository implements UserRepository {
    public static boolean isEnabled() {
        return hasText(System.getenv("AUCTION_DB_URL"))
                && hasText(System.getenv("AUCTION_DB_USER"))
                && hasText(System.getenv("AUCTION_DB_PASSWORD"));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        try (UserDAO userDAO = UserDAO.fromEnvironment()) {
            return userDAO.findByUsername(username);
        } catch (SQLException e) {
            System.err.println("Database user lookup failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> findById(String userId) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        try (UserDAO userDAO = UserDAO.fromEnvironment()) {
            return Optional.ofNullable(userDAO.getUserById(userId));
        } catch (SQLException e) {
            System.err.println("Database user lookup failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<User> findAll() {
        if (!isEnabled()) {
            return List.of();
        }

        try (UserDAO userDAO = UserDAO.fromEnvironment()) {
            return userDAO.getAllUsers();
        } catch (SQLException e) {
            System.err.println("Database user list failed: " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<User> save(User user) {
        if (!isEnabled() || user == null) {
            return Optional.empty();
        }

        try (UserDAO userDAO = UserDAO.fromEnvironment()) {
            userDAO.addUser(user);
            return Optional.of(user);
        } catch (SQLException e) {
            System.err.println("Database user save failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean updateRole(String userId, String role) {
        if (!isEnabled()) {
            return false;
        }

        try (UserDAO userDAO = UserDAO.fromEnvironment()) {
            User user = userDAO.getUserById(userId);
            if (user == null) {
                return false;
            }
            user.setRole(role);
            userDAO.updateUser(user);
            return true;
        } catch (SQLException e) {
            System.err.println("Database role update failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
