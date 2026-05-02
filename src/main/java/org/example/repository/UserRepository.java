package org.example.repository;

import org.example.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);

    default Optional<User> findById(String userId) {
        return Optional.empty();
    }

    default List<User> findAll() {
        return List.of();
    }

    default Optional<User> save(User user) {
        return Optional.empty();
    }

    default boolean updateRole(String userId, String role) {
        return false;
    }
}
