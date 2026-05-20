package org.example.service;

import org.example.exception.InvalidPasswordException;
import org.example.model.Admin;
import org.example.model.Bidder;
import org.example.model.User;
import org.example.repository.DemoUserRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationServiceTest {
    @Test
    void demoAccountsCanLogInWithoutManualRegistration() throws Exception {
        AuthenticationService service = new AuthenticationService(List.of(DemoUserRepository.getInstance()));

        assertEquals("BIDDER", service.loginOrThrow("bidder", "bid123").getRole());
        assertEquals("SELLER", service.loginOrThrow("seller", "sell123").getRole());
        assertEquals("ADMIN", service.loginOrThrow("admin", "admin123").getRole());
    }

    @Test
    void loginHintShowsAvailableDemoCredentials() {
        AuthenticationService service = new AuthenticationService(List.of(DemoUserRepository.getInstance()));

        assertTrue(service.getLoginHint().contains("bidder/bid123"));
        assertTrue(service.getLoginHint().contains("seller/sell123"));
        assertTrue(service.getLoginHint().contains("admin/admin123"));
    }

    @Test
    void loginHintFallsBackToGenericMessageWhenDemoAccountsAreDisabled() {
        AuthenticationService service = new AuthenticationService(List.of(DemoUserRepository.createEmpty()));

        assertTrue(service.getLoginHint().contains("existing account"));
    }

    @Test
    void loginContinuesSearchingRepositoriesUntilPasswordMatches() throws Exception {
        InMemoryUserRepository primaryRepository = new InMemoryUserRepository();
        primaryRepository.save(new Bidder("PRIMARY-ADMIN", "admin", "wrong-password", "db-admin@test.local", 0.0));
        AuthenticationService service = new AuthenticationService(List.of(primaryRepository, DemoUserRepository.getInstance()));

        User loggedIn = service.loginOrThrow("admin", "admin123");

        assertEquals("ADMIN", loggedIn.getRole());
        assertEquals("admin", loggedIn.getUsername());
    }

    @Test
    void loginReturnsPrimaryRepositoryUserAfterFallbackSynchronization() throws Exception {
        UsernameStableRepository primaryRepository = new UsernameStableRepository();
        Bidder placeholder = new Bidder("PRIMARY-ADMIN", "admin", "wrong-password", "db-admin@test.local", 0.0);
        placeholder.setRole("BIDDER");
        primaryRepository.save(placeholder);
        AuthenticationService service = new AuthenticationService(List.of(primaryRepository, DemoUserRepository.getInstance()));

        User loggedIn = service.loginOrThrow("admin", "admin123");

        assertEquals("PRIMARY-ADMIN", loggedIn.getId());
        assertEquals("ADMIN", loggedIn.getRole());
        assertEquals("admin123", loggedIn.getPassword());
    }

    @Test
    void loginFailsWhenNoRepositoryContainsTheSubmittedPassword() {
        InMemoryUserRepository primaryRepository = new InMemoryUserRepository();
        primaryRepository.save(new Bidder("PRIMARY-ADMIN", "admin", "wrong-password", "db-admin@test.local", 0.0));
        AuthenticationService service = new AuthenticationService(List.of(primaryRepository));

        assertThrows(InvalidPasswordException.class, () -> service.loginOrThrow("admin", "admin123"));
    }

    @Test
    void bootstrapPromotesDefaultAdminWhenPersistentStoreHasNoAdminAccount() {
        InMemoryUserRepository primaryRepository = new InMemoryUserRepository();
        Bidder placeholder = new Bidder("PRIMARY-ADMIN", "admin", "custom-pass", "db-admin@test.local", 0.0);
        placeholder.setRole("BIDDER");
        primaryRepository.save(placeholder);

        new AuthenticationService(List.of(primaryRepository, DemoUserRepository.getInstance()), true);

        User admin = primaryRepository.findByUsername("admin").orElseThrow();
        assertEquals("ADMIN", admin.getRole());
        assertEquals("admin123", admin.getPassword());
    }

    @Test
    void allUsersPreferPrimaryRepositoryEntriesWhenUsernamesOverlap() {
        InMemoryUserRepository primaryRepository = new InMemoryUserRepository();
        Bidder primarySeller = new Bidder("PRIMARY-SELLER", "seller", "secret", "seller@test.local", 0.0);
        primarySeller.setRole("SELLER");
        primaryRepository.save(primarySeller);
        AuthenticationService service = new AuthenticationService(List.of(primaryRepository, DemoUserRepository.getInstance()));

        List<User> users = service.getAllUsers();

        long sellerCount = users.stream()
                .filter(user -> "seller".equalsIgnoreCase(user.getUsername()))
                .count();
        assertEquals(1, sellerCount);
        User seller = users.stream()
                .filter(user -> "seller".equalsIgnoreCase(user.getUsername()))
                .findFirst()
                .orElseThrow();
        assertEquals("PRIMARY-SELLER", seller.getId());
    }

    @Test
    void allUsersExcludeDemoOnlyAccountsWhenPersistentRepositoryExists() {
        InMemoryUserRepository primaryRepository = new InMemoryUserRepository();
        Bidder persistentAdmin = new Bidder("PRIMARY-ADMIN", "admin", "secret", "admin@test.local", 0.0);
        persistentAdmin.setRole("ADMIN");
        primaryRepository.save(persistentAdmin);
        AuthenticationService service = new AuthenticationService(List.of(primaryRepository, DemoUserRepository.getInstance()));

        List<User> users = service.getAllUsers();

        assertEquals(1, users.size());
        assertEquals("PRIMARY-ADMIN", users.getFirst().getId());
    }

    private static class InMemoryUserRepository implements UserRepository {
        private final Map<String, User> usersByUsername = new LinkedHashMap<>();

        @Override
        public Optional<User> findByUsername(String username) {
            if (username == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(usersByUsername.get(username.trim().toLowerCase()));
        }

        @Override
        public List<User> findAll() {
            return List.copyOf(usersByUsername.values());
        }

        @Override
        public Optional<User> save(User user) {
            if (user == null || user.getUsername() == null) {
                return Optional.empty();
            }
            usersByUsername.put(user.getUsername().trim().toLowerCase(), user);
            return Optional.of(user);
        }
    }

    private static final class UsernameStableRepository extends InMemoryUserRepository {
        @Override
        public Optional<User> save(User user) {
            Optional<User> existing = findByUsername(user == null ? null : user.getUsername());
            if (existing.isEmpty() || user == null) {
                return super.save(user);
            }

            User current = existing.get();
            User replacement = "ADMIN".equalsIgnoreCase(user.getRole())
                    ? new Admin(current.getId(), user.getUsername(), user.getPassword(), user.getEmail())
                    : new Bidder(current.getId(), user.getUsername(), user.getPassword(), user.getEmail(), 0.0);
            replacement.copyProfileFrom(user);
            replacement.setRole(user.getRole());
            return super.save(replacement);
        }
    }
}
