package org.example.server;

import org.example.dao.AuthSessionDAO;
import org.example.dao.DatabaseConfig;
import org.example.model.User;
import org.example.service.AuthenticationService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ApiSessionService {
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);

    private final SessionStore sessionStore;
    private final UserLookup userLookup;
    private final long tokenLifetimeSeconds;
    private final AtomicLong lastCleanupEpochMillis = new AtomicLong(0L);

    public ApiSessionService() {
        this(resolveTokenLifetimeSeconds(), resolveSessionStore(), AuthenticationService.getInstance()::findById);
    }

    ApiSessionService(long tokenLifetimeSeconds, SessionStore sessionStore, UserLookup userLookup) {
        this.tokenLifetimeSeconds = Math.max(300L, tokenLifetimeSeconds);
        this.sessionStore = sessionStore == null ? new InMemorySessionStore() : sessionStore;
        this.userLookup = userLookup == null ? userId -> Optional.empty() : userLookup;
    }

    public SessionState createSession(User user) {
        if (user == null || user.getId() == null || user.getId().isBlank()) {
            throw new IllegalArgumentException("Cannot create a session without a persistent user id.");
        }

        expireSessions();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(tokenLifetimeSeconds);
        String token = UUID.randomUUID().toString();
        sessionStore.save(new StoredSession(
                UUID.randomUUID().toString(),
                tokenHash(token),
                user.getId(),
                now,
                expiresAt
        ));
        return new SessionState(token, user, now, expiresAt);
    }

    public Optional<SessionState> findSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String trimmedToken = token.trim();
        String tokenHash = tokenHash(trimmedToken);
        Optional<StoredSession> persistedSession = sessionStore.findByTokenHash(tokenHash);
        if (persistedSession.isEmpty()) {
            return Optional.empty();
        }

        StoredSession session = persistedSession.get();
        if (session.expiresAt().isBefore(Instant.now())) {
            sessionStore.revokeByTokenHash(tokenHash);
            return Optional.empty();
        }

        Optional<User> user = userLookup.findById(session.userId());
        if (user.isEmpty()) {
            sessionStore.revokeByTokenHash(tokenHash);
            return Optional.empty();
        }

        return Optional.of(new SessionState(trimmedToken, user.get(), session.createdAt(), session.expiresAt()));
    }

    public Optional<User> findUser(String token) {
        return findSession(token).map(SessionState::user);
    }

    public void replaceUser(User user) {
        // The active user is resolved from the authoritative repository on each request,
        // so profile/role updates are reflected without mutating session records.
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessionStore.revokeByTokenHash(tokenHash(token.trim()));
    }

    public void expireSessions() {
        long now = System.currentTimeMillis();
        long previousCleanup = lastCleanupEpochMillis.get();
        if (now - previousCleanup < CLEANUP_INTERVAL.toMillis()) {
            return;
        }
        if (lastCleanupEpochMillis.compareAndSet(previousCleanup, now)) {
            sessionStore.deleteExpiredSessions();
        }
    }

    static String tokenHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashedBytes.length * 2);
            for (byte hashedByte : hashedBytes) {
                builder.append(String.format("%02x", hashedByte));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not initialize session token hashing.", e);
        }
    }

    private static SessionStore resolveSessionStore() {
        String problem = DatabaseConfig.environmentProblem();
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        if (!DatabaseConfig.hasEnvironmentConfig()) {
            return new InMemorySessionStore();
        }

        try (AuthSessionDAO authSessionDAO = AuthSessionDAO.fromEnvironment()) {
            authSessionDAO.ensureSchema();
            return new DatabaseSessionStore();
        } catch (SQLException e) {
            throw new IllegalStateException("Database session store initialization failed: " + e.getMessage(), e);
        }
    }

    private static long resolveTokenLifetimeSeconds() {
        String rawValue = System.getenv("AUCTION_API_TOKEN_TTL_SECONDS");
        if (rawValue == null || rawValue.isBlank()) {
            return 43_200L;
        }
        try {
            return Long.parseLong(rawValue.trim());
        } catch (NumberFormatException ignored) {
            return 43_200L;
        }
    }

    public record SessionState(String token, User user, Instant createdAt, Instant expiresAt) {
    }

    record StoredSession(
            String sessionId,
            String tokenHash,
            String userId,
            Instant createdAt,
            Instant expiresAt
    ) {
    }

    @FunctionalInterface
    interface UserLookup {
        Optional<User> findById(String userId);
    }

    interface SessionStore {
        void save(StoredSession session);

        Optional<StoredSession> findByTokenHash(String tokenHash);

        void revokeByTokenHash(String tokenHash);

        void deleteExpiredSessions();
    }

    static final class InMemorySessionStore implements SessionStore {
        private final Map<String, StoredSession> sessionsByTokenHash = new ConcurrentHashMap<>();

        @Override
        public void save(StoredSession session) {
            sessionsByTokenHash.put(session.tokenHash(), session);
        }

        @Override
        public Optional<StoredSession> findByTokenHash(String tokenHash) {
            return Optional.ofNullable(sessionsByTokenHash.get(tokenHash));
        }

        @Override
        public void revokeByTokenHash(String tokenHash) {
            sessionsByTokenHash.remove(tokenHash);
        }

        @Override
        public void deleteExpiredSessions() {
            Instant now = Instant.now();
            sessionsByTokenHash.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        }
    }

    private static final class DatabaseSessionStore implements SessionStore {
        @Override
        public void save(StoredSession session) {
            try (AuthSessionDAO authSessionDAO = AuthSessionDAO.fromEnvironment()) {
                authSessionDAO.createSession(
                        session.sessionId(),
                        session.userId(),
                        session.tokenHash(),
                        session.createdAt(),
                        session.expiresAt()
                );
            } catch (SQLException e) {
                throw databaseFailure("Could not persist API session", e);
            }
        }

        @Override
        public Optional<StoredSession> findByTokenHash(String tokenHash) {
            try (AuthSessionDAO authSessionDAO = AuthSessionDAO.fromEnvironment()) {
                return authSessionDAO.findActiveSessionByTokenHash(tokenHash)
                        .map(session -> new StoredSession(
                                session.sessionId(),
                                tokenHash,
                                session.userId(),
                                session.createdAt(),
                                session.expiresAt()
                        ));
            } catch (SQLException e) {
                throw databaseFailure("Could not load API session", e);
            }
        }

        @Override
        public void revokeByTokenHash(String tokenHash) {
            try (AuthSessionDAO authSessionDAO = AuthSessionDAO.fromEnvironment()) {
                authSessionDAO.revokeByTokenHash(tokenHash);
            } catch (SQLException e) {
                throw databaseFailure("Could not revoke API session", e);
            }
        }

        @Override
        public void deleteExpiredSessions() {
            try (AuthSessionDAO authSessionDAO = AuthSessionDAO.fromEnvironment()) {
                authSessionDAO.deleteExpiredSessions();
            } catch (SQLException e) {
                throw databaseFailure("Could not clean up API sessions", e);
            }
        }

        private IllegalStateException databaseFailure(String operation, SQLException e) {
            return new IllegalStateException(operation + ": " + e.getMessage(), e);
        }
    }
}
