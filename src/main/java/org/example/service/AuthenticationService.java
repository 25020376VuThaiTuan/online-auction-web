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
import org.example.util.AccountInputValidator;
import org.example.util.CredentialHasher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AuthenticationService {
    private static final Logger LOGGER = Logger.getLogger(AuthenticationService.class.getName());
    private static final String DEMO_ACCOUNTS_PROPERTY = "auction.demoAccounts.enabled";
    private static final String DEMO_ACCOUNTS_ENV = "AUCTION_DEMO_ACCOUNTS_ENABLED";
    private static final AuthenticationService INSTANCE = new AuthenticationService();

    private final List<UserRepository> repositories = new ArrayList<>();
    private final boolean demoAccountsEnabled;

    private AuthenticationService() {
        this(defaultRepositories(resolveDemoAccountsEnabled()), resolveDemoAccountsEnabled(), resolveDemoAccountsEnabled());
    }

    AuthenticationService(List<UserRepository> repositories) {
        this(repositories, false, containsSeededDemoRepository(repositories));
    }

    AuthenticationService(List<UserRepository> repositories, boolean bootstrapDefaultAccounts) {
        this(repositories, bootstrapDefaultAccounts, bootstrapDefaultAccounts || containsSeededDemoRepository(repositories));
    }

    AuthenticationService(List<UserRepository> repositories, boolean bootstrapDefaultAccounts, boolean demoAccountsEnabled) {
        if (repositories != null) {
            this.repositories.addAll(repositories);
        }
        this.demoAccountsEnabled = demoAccountsEnabled;
        if (bootstrapDefaultAccounts) {
            bootstrapPersistentAccounts();
        }
    }

    public static AuthenticationService getInstance() {
        return INSTANCE;
    }

    public User loginOrThrow(String username, String password) throws UserNotFound, InvalidPasswordException {
        String normalizedUsername = AccountInputValidator.normalizeUsername(username);
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

            User candidateUser = candidate.get();
            if (credentialMatches(safePassword, candidateUser.getPasswordHash())) {
                User userForAuthentication = ensureHashedCredential(candidateUser, safePassword, repository);
                User authenticatedUser = synchronizeWithPrimaryRepository(userForAuthentication, repository);
                recordLogin(authenticatedUser);
                return authenticatedUser;
            }
            invalidPassword = new InvalidPasswordException("Password does not match the selected account.");
        }

        if (invalidPassword != null) {
            throw invalidPassword;
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
        AccountInputValidator.RegistrationInput registration = AccountInputValidator.validateRegistration(
                username,
                password,
                email,
                fullName
        );
        if (findByUsername(registration.username()).isPresent()) {
            throw new IllegalArgumentException("Username is already registered.");
        }
        if (findByEmail(registration.email()).isPresent()) {
            throw new IllegalArgumentException("Email address is already registered.");
        }

        Bidder bidder = new Bidder(
                "U-BID-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                registration.username(),
                CredentialHasher.hash(registration.password()),
                registration.email(),
                0.0
        );
        bidder.setRole("BIDDER");
        bidder.setFullName(registration.fullName());
        saveUserAcrossRepositories(bidder);
        return bidder;
    }

    public synchronized User registerManualSeller(String username, String password, String email, String fullName) {
        AccountInputValidator.RegistrationInput registration = AccountInputValidator.validateRegistration(
                username,
                password,
                email,
                fullName
        );
        if (findByUsername(registration.username()).isPresent()) {
            throw new IllegalArgumentException("Username is already registered.");
        }
        if (findByEmail(registration.email()).isPresent()) {
            throw new IllegalArgumentException("Email address is already registered.");
        }

        Seller seller = new Seller(
                "U-SEL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                registration.username(),
                CredentialHasher.hash(registration.password()),
                registration.email()
        );
        seller.setRole("SELLER");
        seller.setFullName(registration.fullName());
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
                String normalizedUsername = AccountInputValidator.normalizeUsername(user == null ? null : user.getUsername());
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

    public synchronized Optional<User> findByEmail(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (normalizedEmail.isEmpty()) {
            return Optional.empty();
        }
        for (UserRepository repository : repositories) {
            Optional<User> user = repository.findByEmail(normalizedEmail);
            if (user.isPresent()) {
                return user;
            }
        }
        return Optional.empty();
    }

    public synchronized Optional<User> findByUsername(String username) {
        String normalizedUsername = AccountInputValidator.normalizeUsername(username);
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
        if (demoAccountsEnabled) {
            return "Demo accounts: bidder/bid123, seller/sell123, admin/admin123. You can also create a new account.";
        }
        return "Use an existing account or create a new bidder or seller account.";
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
            LOGGER.log(Level.WARNING, "Default account bootstrap skipped.", e);
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

    private static List<UserRepository> defaultRepositories(boolean demoAccountsEnabled) {
        List<UserRepository> repositories = new ArrayList<>();
        if (JdbcUserRepository.isEnabled()) {
            repositories.add(new JdbcUserRepository());
        }
        if (demoAccountsEnabled) {
            repositories.add(DemoUserRepository.getInstance());
        } else if (repositories.isEmpty()) {
            repositories.add(DemoUserRepository.createEmpty());
        }
        return repositories;
    }

    private boolean credentialMatches(String submittedPassword, String storedCredential) {
        if (CredentialHasher.isHashed(storedCredential)) {
            return CredentialHasher.verify(submittedPassword, storedCredential);
        }
        return submittedPassword.equals(storedCredential);
    }

    private User ensureHashedCredential(User user, String submittedPassword, UserRepository sourceRepository) {
        if (user == null || !CredentialHasher.needsRehash(user.getPasswordHash())) {
            return user;
        }

        User upgradedUser = copyWithCredential(user, CredentialHasher.hash(submittedPassword));
        if (sourceRepository != null) {
            boolean updated = sourceRepository.update(upgradedUser);
            if (!updated) {
                sourceRepository.save(upgradedUser);
            }
        }
        return upgradedUser;
    }

    private User copyWithCredential(User source, String credential) {
        User copy = switch (safeRole(source.getRole())) {
            case "ADMIN" -> new Admin(source.getId(), source.getUsername(), credential, source.getEmail());
            case "SELLER" -> new Seller(source.getId(), source.getUsername(), credential, source.getEmail());
            case "BIDDER" -> new Bidder(
                    source.getId(),
                    source.getUsername(),
                    credential,
                    source.getEmail(),
                    source instanceof Bidder bidder ? bidder.getBalance() : 0.0
            );
            default -> new Bidder(
                    source.getId(),
                    source.getUsername(),
                    credential,
                    source.getEmail(),
                    source instanceof Bidder bidder ? bidder.getBalance() : 0.0
            );
        };
        copy.copyProfileFrom(source);
        copy.setRole(source.getRole());
        return copy;
    }

    private UserRepository primaryPersistentRepository() {
        if (repositories.isEmpty()) {
            return null;
        }
        UserRepository repository = repositories.getFirst();
        return repository instanceof DemoUserRepository ? null : repository;
    }

    private static Bidder defaultBidder() {
        Bidder bidder = new Bidder("U-BID-001", "bidder", CredentialHasher.hash("bid123"), "bidder@demo.local", 10_000.0);
        bidder.setRole("BIDDER");
        bidder.setFullName("Primary Bidder");
        return bidder;
    }

    private static Seller defaultSeller() {
        Seller seller = new Seller("U-SEL-001", "seller", CredentialHasher.hash("sell123"), "seller@demo.local");
        seller.setRole("SELLER");
        seller.setFullName("Primary Seller");
        return seller;
    }

    private static Admin defaultAdmin() {
        Admin admin = new Admin("U-ADM-001", "admin", CredentialHasher.hash("admin123"), "admin@demo.local");
        admin.setRole("ADMIN");
        admin.setFullName("Primary Admin");
        return admin;
    }

    private static boolean containsSeededDemoRepository(List<UserRepository> repositories) {
        if (repositories == null) {
            return false;
        }
        return repositories.stream().anyMatch(repository -> repository == DemoUserRepository.getInstance());
    }

    private static String safeRole(String role) {
        return role == null || role.isBlank() ? "BIDDER" : role.trim().toUpperCase();
    }

    private static boolean resolveDemoAccountsEnabled() {
        String configured = System.getProperty(DEMO_ACCOUNTS_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(DEMO_ACCOUNTS_ENV);
        }
        if (configured == null || configured.isBlank()) {
            return !JdbcUserRepository.isEnabled();
        }
        return Boolean.parseBoolean(configured.trim());
    }
}
