package org.example.repository;

import org.example.model.Bidder;
import org.example.model.Seller;
import org.example.model.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void userRepositoryDefaultsAndDemoRepositoryRoleConversionBranchesAreCovered() {
        UserRepository defaults = username -> java.util.Optional.empty();
        User user = new Bidder("default-user", "default_user", "hash", "default@test.local", 5.0);

        assertTrue(defaults.findByEmail("default@test.local").isEmpty());
        assertTrue(defaults.findById("default-user").isEmpty());
        assertEquals(List.of(), defaults.findAll());
        assertTrue(defaults.save(user).isEmpty());
        assertFalse(defaults.update(user));
        assertFalse(defaults.updateRole("default-user", "ADMIN"));
        assertFalse(defaults.recordLogin("default-user"));

        DemoUserRepository repository = DemoUserRepository.createEmpty();
        assertTrue(repository.findByUsername(" ").isEmpty());
        assertTrue(repository.findByEmail(" ").isEmpty());
        assertTrue(repository.save(null).isEmpty());

        Bidder bidder = new Bidder("demo-user", "Demo_User", "hash", "demo@test.local", 42.0);
        bidder.setFullName("Demo User");
        assertSame(bidder, repository.save(bidder).orElseThrow());
        assertTrue(repository.findByUsername(" demo_user ").isPresent());
        assertTrue(repository.findByEmail(" DEMO@test.local ").isPresent());
        assertTrue(repository.findById("demo-user").isPresent());
        assertTrue(repository.recordLogin("demo-user"));
        assertFalse(repository.recordLogin("missing"));
        assertFalse(repository.updateRole("missing", "ADMIN"));

        assertTrue(repository.updateRole("demo-user", "SELLER"));
        assertTrue(repository.findById("demo-user").orElseThrow() instanceof Seller);
        assertTrue(repository.updateRole("demo-user", "ADMIN"));
        assertEquals("ADMIN", repository.findById("demo-user").orElseThrow().getRole());
        assertTrue(repository.updateRole("demo-user", null));
        assertEquals("BIDDER", repository.findById("demo-user").orElseThrow().getRole());
        assertTrue(repository.updateRole("demo-user", "unknown"));
        assertEquals("UNKNOWN", repository.findById("demo-user").orElseThrow().getRole());
        assertEquals(1, repository.findAll().size());
    }
}
