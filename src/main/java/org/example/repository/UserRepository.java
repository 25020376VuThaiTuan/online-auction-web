package org.example.repository;

import org.example.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);

    default Optional<User> findByEmail(String email) {
        return Optional.empty();
    }

    default Optional<User> findById(String userId) {
        return Optional.empty();
    }

    default List<User> findAll() {
        return List.of();
    }

    default Optional<User> save(User user) {
        return Optional.empty();
    }

    default boolean update(User user) {
        return false;
    }

    default boolean updateRole(String userId, String role) {
        return false;
    }

    default boolean updateAccountBanned(String userId, boolean banned) {
        return false;
    }

    default boolean recordLogin(String userId) {
        return false;
    }

    default boolean savePasswordRecoveryCode(String userId, String recoveryCodeHash, LocalDateTime expiresAt) {
        return false;
    }

    default boolean consumePasswordRecoveryCode(String userId, String recoveryCode) {
        return false;
    }
}
