package org.example.server;

import org.example.model.User;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ApiSessionService {
    private final Map<String, SessionState> sessionsByToken = new ConcurrentHashMap<>();
    private final long tokenLifetimeSeconds;

    public ApiSessionService() {
        this(resolveTokenLifetimeSeconds());
    }

    ApiSessionService(long tokenLifetimeSeconds) {
        this.tokenLifetimeSeconds = Math.max(300L, tokenLifetimeSeconds);
    }

    public SessionState createSession(User user) {
        expireSessions();
        Instant now = Instant.now();
        SessionState sessionState = new SessionState(
                UUID.randomUUID().toString(),
                user,
                now,
                now.plusSeconds(tokenLifetimeSeconds)
        );
        sessionsByToken.put(sessionState.token(), sessionState);
        return sessionState;
    }

    public Optional<SessionState> findSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        SessionState sessionState = sessionsByToken.get(token.trim());
        if (sessionState == null) {
            return Optional.empty();
        }
        if (sessionState.expiresAt().isBefore(Instant.now())) {
            sessionsByToken.remove(sessionState.token());
            return Optional.empty();
        }
        return Optional.of(sessionState);
    }

    public Optional<User> findUser(String token) {
        return findSession(token).map(SessionState::user);
    }

    public void replaceUser(User user) {
        if (user == null || user.getId() == null || user.getId().isBlank()) {
            return;
        }

        sessionsByToken.replaceAll((token, session) -> {
            User sessionUser = session.user();
            if (sessionUser == null || !user.getId().equals(sessionUser.getId())) {
                return session;
            }
            return new SessionState(session.token(), user, session.createdAt(), session.expiresAt());
        });
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessionsByToken.remove(token.trim());
    }

    public void expireSessions() {
        Instant now = Instant.now();
        sessionsByToken.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    public record SessionState(String token, User user, Instant createdAt, Instant expiresAt) {
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
}
