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

    private final DemoUserRepository demoUserRepository = DemoUserRepository.getInstance();
    private final List<UserRepository> repositories = new ArrayList<>();

    private AuthenticationService() {
        if (JdbcUserRepository.isEnabled()) {
            repositories.add(new JdbcUserRepository());
        }
        repositories.add(demoUserRepository);
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

        for (UserRepository repository : repositories) {
            Optional<User> candidate = repository.findByUsername(normalizedUsername);
            if (candidate.isEmpty()) {
                continue;
            }

            if (!safePassword.equals(candidate.get().getPassword())) {
                throw new InvalidPasswordException("Password does not match the selected account.");
            }

            return candidate.get();
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
                10_000.0
        );
        bidder.setRole("BIDDER");
        bidder.setFullName(fullName);
        demoUserRepository.save(bidder);
        return bidder;
    }

    public synchronized User loginWithGoogleToken(String googleToken) {
        String normalizedToken = normalize(googleToken);
        if (normalizedToken.isEmpty()) {
            throw new IllegalArgumentException("Google token is required.");
        }

        String suffix = normalizedToken.length() <= 10
                ? normalizedToken
                : normalizedToken.substring(0, 10);
        String username = "google_" + suffix;
        Optional<User> existing = findByUsername(username);
        if (existing.isPresent()) {
            return existing.get();
        }

        Bidder bidder = new Bidder(
                "U-GGL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                username,
                normalizedToken,
                username + "@token.local",
                12_500.0
        );
        bidder.setRole("BIDDER");
        bidder.setFullName("Google Token User");
        bidder.setAvatarUrl("google-token://" + suffix);
        demoUserRepository.save(bidder);
        return bidder;
    }

    public synchronized List<User> getAllUsers() {
        Map<String, User> usersById = new LinkedHashMap<>();
        for (UserRepository repository : repositories) {
            for (User user : repository.findAll()) {
                usersById.putIfAbsent(user.getId(), user);
            }
        }
        return new ArrayList<>(usersById.values());
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

    public String getLoginHint() {
        return "Demo accounts: bidder/bid123, seller/sell123, admin/admin123";
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
