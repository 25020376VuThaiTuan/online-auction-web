package org.example.service;

import org.example.exception.InvalidPasswordException;
import org.example.exception.UserNotFound;
import org.example.model.Admin;
import org.example.model.Bidder;
import org.example.model.User;
import org.example.repository.DemoUserRepository;
import org.example.repository.UserRepository;
import org.example.util.CredentialHasher;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationServiceTest {
    @Test
    void defaultLocalServiceSeedsDemoAccountsWhenNoDatabaseIsConfigured() throws Exception {
        String previousDemoAccounts = System.getProperty("auction.demoAccounts.enabled");
        String previousDatabaseDisabled = System.getProperty("auction.db.disabled");
        try {
            System.clearProperty("auction.demoAccounts.enabled");
            System.setProperty("auction.db.disabled", "true");
            AuthenticationService service = newDefaultAuthenticationService();

            assertEquals("BIDDER", service.loginOrThrow("bidder", "bid123").getRole());
            assertTrue(service.getLoginHint().contains("bidder/bid123"));
        } finally {
            restoreProperty("auction.demoAccounts.enabled", previousDemoAccounts);
            restoreProperty("auction.db.disabled", previousDatabaseDisabled);
        }
    }

    @Test
    void explicitDemoAccountOptOutKeepsLocalRepositoryEmpty() throws Exception {
        String previousDemoAccounts = System.getProperty("auction.demoAccounts.enabled");
        String previousDatabaseDisabled = System.getProperty("auction.db.disabled");
        try {
            System.setProperty("auction.demoAccounts.enabled", "false");
            System.setProperty("auction.db.disabled", "true");
            AuthenticationService service = newDefaultAuthenticationService();

            assertThrows(UserNotFound.class, () -> service.loginOrThrow("bidder", "bid123"));
            assertTrue(service.getLoginHint().contains("existing account"));
        } finally {
            restoreProperty("auction.demoAccounts.enabled", previousDemoAccounts);
            restoreProperty("auction.db.disabled", previousDatabaseDisabled);
        }
    }

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
        assertTrue(CredentialHasher.verify("admin123", loggedIn.getPasswordHash()));
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
        assertTrue(CredentialHasher.verify("admin123", admin.getPasswordHash()));
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

    @Test
    void registrationRejectsInvalidEmailFormat() {
        AuthenticationService service = new AuthenticationService(List.of(DemoUserRepository.createEmpty()));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.registerManualBidder("new_user", "secure123", "not-an-email", "New User")
        );

        assertEquals("Email address format is invalid.", exception.getMessage());
    }

    @Test
    void registrationRejectsShortPasswords() {
        AuthenticationService service = new AuthenticationService(List.of(DemoUserRepository.createEmpty()));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.registerManualBidder("new_user", "12345", "new@test.local", "New User")
        );

        assertEquals("Password must be at least 6 characters.", exception.getMessage());
    }

    @Test
    void registrationRejectsUnsupportedUsernameCharacters() {
        AuthenticationService service = new AuthenticationService(List.of(DemoUserRepository.createEmpty()));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.registerManualSeller("bad user!", "secure123", "seller@test.local", "Seller")
        );

        assertEquals(
                "Username must be 3-32 characters and use only letters, numbers, dot, underscore, or hyphen.",
                exception.getMessage()
        );
    }

    @Test
    void registrationRejectsMissingFullName() {
        AuthenticationService service = new AuthenticationService(List.of(DemoUserRepository.createEmpty()));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.registerManualBidder("new_user", "secure123", "new@test.local", " ")
        );

        assertEquals("Full name is required.", exception.getMessage());
    }

    @Test
    void registrationAllowsFullNameThatMatchesUsernameAfterNormalization() {
        AuthenticationService service = new AuthenticationService(List.of(DemoUserRepository.createEmpty()));

        User user = service.registerManualSeller("john.doe", "secure123", "seller@test.local", "John Doe");

        assertEquals("john.doe", user.getUsername());
        assertEquals("John Doe", user.getFullName());
        assertTrue(CredentialHasher.verify("secure123", user.getPasswordHash()));
        assertTrue(user.getPasswordHash().startsWith("$2a$"));
    }

    @Test
    void loginMigratesLegacyPbkdf2PasswordHashToBcrypt() throws Exception {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        repository.save(new Bidder(
                "LEGACY-PBKDF2",
                "legacy_pbkdf2",
                CredentialHasher.hashLegacyPbkdf2("legacy123"),
                "legacy@test.local",
                0.0
        ));
        AuthenticationService service = new AuthenticationService(List.of(repository));

        User loggedIn = service.loginOrThrow("legacy_pbkdf2", "legacy123");

        assertTrue(CredentialHasher.verify("legacy123", loggedIn.getPasswordHash()));
        assertTrue(loggedIn.getPasswordHash().startsWith("$2a$"));
    }

    @Test
    void loginMigratesLegacyPlaintextPasswordToBcrypt() throws Exception {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        repository.save(new Bidder(
                "LEGACY-PLAINTEXT",
                "legacy_plaintext",
                "legacy123",
                "legacy-plain@test.local",
                0.0
        ));
        AuthenticationService service = new AuthenticationService(List.of(repository));

        User loggedIn = service.loginOrThrow("legacy_plaintext", "legacy123");

        assertTrue(CredentialHasher.verify("legacy123", loggedIn.getPasswordHash()));
        assertTrue(loggedIn.getPasswordHash().startsWith("$2a$"));
    }

    @Test
    void registrationRejectsDuplicateEmailAddresses() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        repository.save(new Bidder("U-BID-001", "first_user", "secure123", "shared@test.local", 0.0));
        AuthenticationService service = new AuthenticationService(List.of(repository));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.registerManualSeller("second_user", "secure123", "shared@test.local", "Seller Two")
        );

        assertEquals("Email address is already registered.", exception.getMessage());
    }

    private AuthenticationService newDefaultAuthenticationService() throws Exception {
        Constructor<AuthenticationService> constructor = AuthenticationService.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void restoreProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
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
        public Optional<User> findByEmail(String email) {
            if (email == null) {
                return Optional.empty();
            }
            String normalizedEmail = email.trim().toLowerCase();
            return usersByUsername.values().stream()
                    .filter(user -> user.getEmail() != null && user.getEmail().trim().toLowerCase().equals(normalizedEmail))
                    .findFirst();
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
                    ? new Admin(current.getId(), user.getUsername(), user.getPasswordHash(), user.getEmail())
                    : new Bidder(current.getId(), user.getUsername(), user.getPasswordHash(), user.getEmail(), 0.0);
            replacement.copyProfileFrom(user);
            replacement.setRole(user.getRole());
            return super.save(replacement);
        }
    }
}
