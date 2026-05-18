package org.example.service;

import org.example.exception.InvalidPasswordException;
import org.example.exception.UserNotFound;
import org.example.model.Admin;
import org.example.model.Bidder;
import org.example.model.Seller;
import org.example.model.User;
import org.example.repository.DemoUserRepository;
import org.example.repository.JdbcUserRepository;
import org.example.repository.UserRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AuthenticationService {
    private static final AuthenticationService INSTANCE = new AuthenticationService();

    private final List<UserRepository> repositories = new ArrayList<>();

    private AuthenticationService() {
        this(defaultRepositories(), true);
    }

    AuthenticationService(List<UserRepository> repositories) {
        this(repositories, false);
    }

    AuthenticationService(List<UserRepository> repositories, boolean bootstrapDefaultAccounts) {
        if (repositories != null) {
            this.repositories.addAll(repositories);
        }
        if (bootstrapDefaultAccounts) {
            bootstrapPersistentAccounts();
        }
    }

    public static AuthenticationService getInstance() {
        return INSTANCE;
    }

    public User loginOrThrow(String username, String password) throws UserNotFound, InvalidPasswordException {
        String normalizedUsername = normalize(username);
        String safePassword = password == null ? "" : password.trim();

        if (normalizedUsername.isEmpty() || safePassword.isEmpty()) {
            throw new InvalidPasswordException("Username and password are required.");
        }

        boolean usernameFound = false;
        InvalidPasswordException invalidPassword = null;

        for (UserRepository repository : repositories) {
            Optional<User> candidate = repository.findByUsername(normalizedUsername);
            if (candidate.isEmpty()) {
                continue;
            }
            usernameFound = true;

            if (safePassword.equals(candidate.get().getPassword())) {
                User authenticatedUser = synchronizeWithPrimaryRepository(candidate.get(), repository);
                recordLogin(authenticatedUser);
                return authenticatedUser;
            }
            invalidPassword = new InvalidPasswordException("Password does not match the selected account.");
        }

        if (invalidPassword != null || usernameFound) {
            throw invalidPassword == null
                    ? new InvalidPasswordException("Password does not match the selected account.")
                    : invalidPassword;
        }
        throw new UserNotFound("No account exists for username: " + normalizedUsername);
    }

    public Optional<User> authenticate(String username, String password) {
        try {
            return Optional.of(loginOrThrow(username, password));
        } catch (UserNotFound | InvalidPasswordException e) {
            return Optional.empty();
        }
    }

    public synchronized User registerManualBidder(String username, String password, String email, String fullName) {
        String normalizedUsername = normalize(username);
        if (normalizedUsername.isEmpty() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Username and password are required.");
        }
        if (findByUsername(normalizedUsername).isPresent()) {
            throw new IllegalArgumentException("Username is already registered.");
        }

        Bidder bidder = new Bidder(
                "U-BID-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                normalizedUsername,
                password.trim(),
                email == null ? "" : email.trim(),
                0.0
        );
        bidder.setRole("BIDDER");
        bidder.setFullName(fullName);
        saveUserAcrossRepositories(bidder);
        return bidder;
    }

    public synchronized User registerManualSeller(String username, String password, String email, String fullName) {
        String normalizedUsername = normalize(username);
        if (normalizedUsername.isEmpty() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Username and password are required.");
        }
        if (findByUsername(normalizedUsername).isPresent()) {
            throw new IllegalArgumentException("Username is already registered.");
        }

        Seller seller = new Seller(
                "U-SEL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                normalizedUsername,
                password.trim(),
                email == null ? "" : email.trim()
        );
        seller.setRole("SELLER");
        seller.setFullName(fullName);
        saveUserAcrossRepositories(seller);
        return seller;
    }

    public synchronized List<User> getAllUsers() {
        UserRepository persistentRepository = primaryPersistentRepository();
        if (persistentRepository != null) {
            return new ArrayList<>(persistentRepository.findAll());
        }

        Map<String, User> usersByUsername = new LinkedHashMap<>();
        for (UserRepository repository : repositories) {
            for (User user : repository.findAll()) {
                String normalizedUsername = normalize(user == null ? null : user.getUsername());
                if (normalizedUsername.isEmpty()) {
                    continue;
                }
                usersByUsername.putIfAbsent(normalizedUsername, user);
            }
        }
        return new ArrayList<>(usersByUsername.values());
    }

    public synchronized Optional<User> findById(String userId) {
        for (UserRepository repository : repositories) {
            Optional<User> user = repository.findById(userId);
            if (user.isPresent()) {
                return user;
            }
        }
        return Optional.empty();
    }

    public synchronized Optional<User> findByUsername(String username) {
        String normalizedUsername = normalize(username);
        for (UserRepository repository : repositories) {
            Optional<User> user = repository.findByUsername(normalizedUsername);
            if (user.isPresent()) {
                return user;
            }
        }
        return Optional.empty();
    }

    public synchronized boolean updateUserRole(String userId, String role) {
        boolean updated = false;
        for (UserRepository repository : repositories) {
            updated = repository.updateRole(userId, role) || updated;
        }
        return updated;
    }

    public synchronized boolean updateUser(User user) {
        if (user == null) {
            return false;
        }

        boolean updated = false;
        for (UserRepository repository : repositories) {
            updated = repository.update(user) || updated;
        }
        return updated;
    }

    public String getLoginHint() {
        return "Demo accounts: bidder/bid123, seller/sell123, admin/admin123. You can also create a new account.";
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private void saveUserAcrossRepositories(User user) {
        boolean saved = false;
        for (UserRepository repository : repositories) {
            saved = repository.save(user).isPresent() || saved;
        }
        if (!saved) {
            throw new IllegalStateException("User could not be saved.");
        }
    }

    private void recordLogin(User user) {
        for (UserRepository repository : repositories) {
            repository.recordLogin(user.getId());
        }
    }

    private User synchronizeWithPrimaryRepository(User user, UserRepository sourceRepository) {
        UserRepository primaryRepository = primaryPersistentRepository();
        if (user == null || primaryRepository == null || primaryRepository == sourceRepository) {
            return user;
        }

        primaryRepository.save(user);
        return primaryRepository.findByUsername(user.getUsername()).orElse(user);
    }

    private void bootstrapPersistentAccounts() {
        UserRepository persistentRepository = primaryPersistentRepository();
        if (persistentRepository == null) {
            return;
        }

        try {
            ensureDefaultUserPresent(persistentRepository, defaultBidder());
            ensureDefaultUserPresent(persistentRepository, defaultSeller());
            ensureAccessibleAdminAccount(persistentRepository);
        } catch (RuntimeException e) {
            System.out.println("Default account bootstrap skipped: " + e.getMessage());
        }
    }

    private void ensureAccessibleAdminAccount(UserRepository repository) {
        boolean hasAdmin = repository.findAll().stream()
                .anyMatch(user -> "ADMIN".equalsIgnoreCase(user.getRole()));
        if (hasAdmin) {
            return;
        }
        repository.save(defaultAdmin());
    }

    private void ensureDefaultUserPresent(UserRepository repository, User user) {
        if (repository.findByUsername(user.getUsername()).isPresent()) {
            return;
        }
        repository.save(user);
    }

    private static List<UserRepository> defaultRepositories() {
        List<UserRepository> repositories = new ArrayList<>();
        if (JdbcUserRepository.isEnabled()) {
            repositories.add(new JdbcUserRepository());
        }
        repositories.add(DemoUserRepository.getInstance());
        return repositories;
    }

    private UserRepository primaryPersistentRepository() {
        if (repositories.isEmpty()) {
            return null;
        }
        UserRepository repository = repositories.getFirst();
        return repository instanceof DemoUserRepository ? null : repository;
    }

    private static Bidder defaultBidder() {
        Bidder bidder = new Bidder("U-BID-001", "bidder", "bid123", "bidder@demo.local", 10_000.0);
        bidder.setRole("BIDDER");
        bidder.setFullName("Primary Bidder");
        return bidder;
    }

    private static Seller defaultSeller() {
        Seller seller = new Seller("U-SEL-001", "seller", "sell123", "seller@demo.local");
        seller.setRole("SELLER");
        seller.setFullName("Primary Seller");
        return seller;
    }

    private static Admin defaultAdmin() {
        Admin admin = new Admin("U-ADM-001", "admin", "admin123", "admin@demo.local");
        admin.setRole("ADMIN");
        admin.setFullName("Primary Admin");
        return admin;
    }
}
