package org.example.repository;

import org.example.dao.DatabaseConfig;
import org.example.dao.UserDAO;
import org.example.model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class JdbcUserRepository implements UserRepository {
    public static boolean isEnabled() {
        String problem = DatabaseConfig.environmentProblem();
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        return DatabaseConfig.hasEnvironmentConfig();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        try (UserDAO userDAO = UserDAO.fromEnvironment()) {
            return userDAO.findByUsername(username);
        } catch (SQLException e) {
            throw databaseFailure("Database user lookup failed", e);
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
            throw databaseFailure("Database user lookup failed", e);
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
            throw databaseFailure("Database user list failed", e);
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
            throw databaseFailure("Database user save failed", e);
        }
    }

    @Override
    public boolean update(User user) {
        if (!isEnabled() || user == null) {
            return false;
        }

        try (UserDAO userDAO = UserDAO.fromEnvironment()) {
            userDAO.updateUser(user);
            return true;
        } catch (SQLException e) {
            throw databaseFailure("Database user update failed", e);
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
            throw databaseFailure("Database role update failed", e);
        }
    }

    @Override
    public boolean recordLogin(String userId) {
        if (!isEnabled()) {
            return false;
        }

        try (UserDAO userDAO = UserDAO.fromEnvironment()) {
            userDAO.recordLogin(userId);
            return true;
        } catch (SQLException e) {
            throw databaseFailure("Database login timestamp update failed", e);
        }
    }

    private IllegalStateException databaseFailure(String operation, SQLException e) {
        return new IllegalStateException(operation + ": " + e.getMessage(), e);
    }

}
