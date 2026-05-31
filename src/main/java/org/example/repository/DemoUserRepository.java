package org.example.repository;

import org.example.model.Admin;
import org.example.model.Bidder;
import org.example.model.Seller;
import org.example.model.User;
import org.example.util.CredentialHasher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DemoUserRepository implements UserRepository {
    private static final DemoUserRepository INSTANCE = new DemoUserRepository(true);

    private final Map<String, User> usersByUsername = new LinkedHashMap<>();
    private final Map<String, PasswordRecoveryState> passwordRecoveryByUserId = new LinkedHashMap<>();

    private DemoUserRepository(boolean seedDefaults) {
        if (!seedDefaults) {
            return;
        }
        seedUser(createBidder("U-BID-001", "bidder", CredentialHasher.hash("bid123"), "bidder@demo.local", 10_000.0, "Primary Bidder"));
        seedUser(createSeller("U-SEL-001", "seller", CredentialHasher.hash("sell123"), "seller@demo.local", "Primary Seller"));
        seedUser(createAdmin("U-ADM-001", "admin", CredentialHasher.hash("admin123"), "admin@demo.local", "Primary Admin"));
    }

    public static DemoUserRepository getInstance() {
        return INSTANCE;
    }

    public static DemoUserRepository createEmpty() {
        return new DemoUserRepository(false);
    }

    @Override
    public synchronized Optional<User> findByUsername(String username) {
        String normalizedUsername = normalize(username);
        if (normalizedUsername.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(usersByUsername.get(normalizedUsername));
    }

    @Override
    public synchronized Optional<User> findByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty()) {
            return Optional.empty();
        }
        return usersByUsername.values().stream()
                .filter(user -> normalizedEmail.equals(normalizeEmail(user.getEmail())))
                .findFirst();
    }

    @Override
    public synchronized Optional<User> findById(String userId) {
        return usersByUsername.values().stream()
                .filter(user -> user.getId().equals(userId))
                .findFirst();
    }

    @Override
    public synchronized List<User> findAll() {
        return new ArrayList<>(usersByUsername.values());
    }

    @Override
    public synchronized Optional<User> save(User user) {
        if (user == null) {
            return Optional.empty();
        }
        String normalizedUsername = normalize(user.getUsername());
        if (normalizedUsername.isEmpty()) {
            return Optional.empty();
        }
        usersByUsername.put(normalizedUsername, user);
        return Optional.of(user);
    }

    @Override
    public synchronized boolean update(User user) {
        return save(user).isPresent();
    }

    @Override
    public synchronized boolean updateRole(String userId, String role) {
        Optional<User> existing = findById(userId);
        if (existing.isEmpty()) {
            return false;
        }

        User converted = convertRole(existing.get(), role);
        usersByUsername.put(normalize(converted.getUsername()), converted);
        return true;
    }

    @Override
    public synchronized boolean updateAccountBanned(String userId, boolean banned) {
        Optional<User> existing = findById(userId);
        if (existing.isEmpty()) {
            return false;
        }
        existing.get().setAccountBanned(banned);
        return true;
    }

    @Override
    public synchronized boolean recordLogin(String userId) {
        return findById(userId).isPresent();
    }

    @Override
    public synchronized boolean savePasswordRecoveryCode(String userId, String recoveryCodeHash, LocalDateTime expiresAt) {
        if (userId == null || userId.isBlank() || !CredentialHasher.isHashed(recoveryCodeHash) || expiresAt == null) {
            return false;
        }
        if (findById(userId).isEmpty()) {
            return false;
        }
        passwordRecoveryByUserId.put(userId, new PasswordRecoveryState(recoveryCodeHash, expiresAt));
        return true;
    }

    @Override
    public synchronized boolean consumePasswordRecoveryCode(String userId, String recoveryCode) {
        PasswordRecoveryState state = passwordRecoveryByUserId.get(userId);
        if (state == null) {
            return false;
        }
        if (state.expiresAt().isBefore(LocalDateTime.now())) {
            passwordRecoveryByUserId.remove(userId);
            return false;
        }
        if (!recoveryCodeAccepted(recoveryCode, state.recoveryCodeHash())) {
            return false;
        }
        passwordRecoveryByUserId.remove(userId);
        return true;
    }

    private void seedUser(User user) {
        usersByUsername.put(normalize(user.getUsername()), user);
    }

    private static Bidder createBidder(
            String id,
            String username,
            String password,
            String email,
            double balance,
            String fullName
    ) {
        Bidder bidder = new Bidder(id, username, password, email, balance);
        bidder.setRole("BIDDER");
        bidder.setFullName(fullName);
        return bidder;
    }

    private static Seller createSeller(String id, String username, String password, String email, String fullName) {
        Seller seller = new Seller(id, username, password, email);
        seller.setRole("SELLER");
        seller.setFullName(fullName);
        return seller;
    }

    private static Admin createAdmin(String id, String username, String password, String email, String fullName) {
        Admin admin = new Admin(id, username, password, email);
        admin.setRole("ADMIN");
        admin.setFullName(fullName);
        return admin;
    }

    private User convertRole(User source, String role) {
        String safeRole = role == null ? "BIDDER" : role.trim().toUpperCase();
        User converted = switch (safeRole) {
            case "ADMIN" -> new Admin(source.getId(), source.getUsername(), source.getPasswordHash(), source.getEmail());
            case "SELLER" -> new Seller(source.getId(), source.getUsername(), source.getPasswordHash(), source.getEmail());
            case "BIDDER" -> new Bidder(source.getId(), source.getUsername(), source.getPasswordHash(), source.getEmail(),
                    source instanceof Bidder bidder ? bidder.getBalance() : 0.0);
            default -> new Bidder(source.getId(), source.getUsername(), source.getPasswordHash(), source.getEmail(),
                    source instanceof Bidder bidder ? bidder.getBalance() : 0.0);
        };
        converted.copyProfileFrom(source);
        converted.setRole(safeRole);
        return converted;
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private boolean recoveryCodeAccepted(String recoveryCode, String storedHash) {
        if (recoveryCode == null || recoveryCode.isBlank() || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        return CredentialHasher.isHashed(storedHash)
                ? CredentialHasher.verify(recoveryCode, storedHash)
                : recoveryCode.equals(storedHash);
    }

    private record PasswordRecoveryState(String recoveryCodeHash, LocalDateTime expiresAt) {
    }
}
