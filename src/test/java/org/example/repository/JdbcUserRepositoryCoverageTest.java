package org.example.repository;

import org.example.model.Bidder;
import org.example.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcUserRepositoryCoverageTest {
    @Test
    void disabledDatabaseConfigMakesRepositoryMethodsNoopSafely() {
        String previousDisabled = System.getProperty("auction.db.disabled");
        try {
            System.setProperty("auction.db.disabled", "true");
            JdbcUserRepository repository = new JdbcUserRepository();
            User user = new Bidder("jdbc-user-1", "jdbc_user", "hash", "jdbc@test.local", 0.0);

            assertFalse(JdbcUserRepository.isEnabled());
            assertTrue(repository.findByUsername("jdbc_user").isEmpty());
            assertTrue(repository.findByEmail("jdbc@test.local").isEmpty());
            assertTrue(repository.findById("jdbc-user-1").isEmpty());
            assertTrue(repository.findAll().isEmpty());
            assertTrue(repository.save(user).isEmpty());
            assertTrue(repository.save(null).isEmpty());
            assertFalse(repository.update(user));
            assertFalse(repository.update(null));
            assertFalse(repository.updateRole("jdbc-user-1", "ADMIN"));
            assertFalse(repository.recordLogin("jdbc-user-1"));
        } finally {
            if (previousDisabled == null) {
                System.clearProperty("auction.db.disabled");
            } else {
                System.setProperty("auction.db.disabled", previousDisabled);
            }
        }
    }
}
