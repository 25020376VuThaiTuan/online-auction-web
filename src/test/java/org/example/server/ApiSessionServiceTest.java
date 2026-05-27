package org.example.server;

import org.example.model.Bidder;
import org.example.model.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiSessionServiceTest {
    @Test
    void sessionCanBeResolvedAcrossServiceInstancesUsingSharedStore() {
        Map<String, User> usersById = new ConcurrentHashMap<>();
        User user = bidder("U-BID-900", "remote_bidder");
        usersById.put(user.getId(), user);

        ApiSessionService.SessionStore sharedStore = new ApiSessionService.InMemorySessionStore();
        ApiSessionService firstService = new ApiSessionService(3600, sharedStore, userId -> Optional.ofNullable(usersById.get(userId)));
        ApiSessionService.SessionState session = firstService.createSession(user);

        ApiSessionService secondService = new ApiSessionService(3600, sharedStore, userId -> Optional.ofNullable(usersById.get(userId)));
        Optional<User> resolvedUser = secondService.findUser(session.token());

        assertTrue(resolvedUser.isPresent());
        assertEquals(user.getId(), resolvedUser.get().getId());
        assertEquals(user.getUsername(), resolvedUser.get().getUsername());
    }

    @Test
    void revokedSessionCannotBeResolved() {
        User user = bidder("U-BID-901", "revoked_bidder");
        ApiSessionService service = new ApiSessionService(
                3600,
                new ApiSessionService.InMemorySessionStore(),
                userId -> Optional.of(user)
        );

        ApiSessionService.SessionState session = service.createSession(user);
        service.revoke(session.token());

        assertFalse(service.findUser(session.token()).isPresent());
    }

    @Test
    void expiredSessionIsRejectedAndRemoved() {
        String rawToken = "expired-token";
        String tokenHash = ApiSessionService.tokenHash(rawToken);
        User user = bidder("U-BID-902", "expired_bidder");
        ApiSessionService.InMemorySessionStore store = new ApiSessionService.InMemorySessionStore();
        store.save(new ApiSessionService.StoredSession(
                "session-expired-1",
                tokenHash,
                user.getId(),
                Instant.now().minusSeconds(600),
                Instant.now().minusSeconds(60)
        ));

        ApiSessionService service = new ApiSessionService(3600, store, userId -> Optional.of(user));

        assertFalse(service.findSession(rawToken).isPresent());
        assertFalse(store.findByTokenHash(tokenHash).isPresent());
    }

    @Test
    void invalidTokensUsersAndMissingUsersUseSafeNoopPaths() {
        User user = bidder("U-BID-903", "missing_user_bidder");
        ApiSessionService.InMemorySessionStore store = new ApiSessionService.InMemorySessionStore();
        ApiSessionService service = new ApiSessionService(1, store, userId -> Optional.empty());

        assertFalse(service.findSession(null).isPresent());
        assertFalse(service.findSession(" ").isPresent());
        service.revoke(null);
        service.revoke(" ");
        service.replaceUser(user);

        assertThrows(IllegalArgumentException.class, () -> service.createSession(null));
        assertThrows(IllegalArgumentException.class, () -> service.createSession(new Bidder("", "blank", "secret", "blank@test.local", 0.0)));

        ApiSessionService.SessionState session = service.createSession(user);

        assertEquals(300L, session.expiresAt().getEpochSecond() - session.createdAt().getEpochSecond());
        assertFalse(service.findSession(session.token()).isPresent());
        assertFalse(store.findByTokenHash(ApiSessionService.tokenHash(session.token())).isPresent());
    }

    @Test
    void databaseSessionStoreWrapsSqlFailuresWithOperationMessages() throws Exception {
        String previousDisabled = System.getProperty("auction.db.disabled");
        try {
            System.setProperty("auction.db.disabled", "true");
            Object databaseStore = newDatabaseSessionStore();
            ApiSessionService.StoredSession session = new ApiSessionService.StoredSession(
                    "session-database-failure",
                    "hash-database-failure",
                    "user-database-failure",
                    Instant.now(),
                    Instant.now().plusSeconds(600)
            );

            assertDatabaseFailure(databaseStore, "save",
                    new Class<?>[]{ApiSessionService.StoredSession.class},
                    "Could not persist API session",
                    session);
            assertDatabaseFailure(databaseStore, "findByTokenHash",
                    new Class<?>[]{String.class},
                    "Could not load API session",
                    "hash-database-failure");
            assertDatabaseFailure(databaseStore, "revokeByTokenHash",
                    new Class<?>[]{String.class},
                    "Could not revoke API session",
                    "hash-database-failure");
            assertDatabaseFailure(databaseStore, "deleteExpiredSessions",
                    new Class<?>[0],
                    "Could not clean up API sessions");
        } finally {
            if (previousDisabled == null) {
                System.clearProperty("auction.db.disabled");
            } else {
                System.setProperty("auction.db.disabled", previousDisabled);
            }
        }
    }

    private static Object newDatabaseSessionStore() throws Exception {
        Class<?> type = Class.forName("org.example.server.ApiSessionService$DatabaseSessionStore");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void assertDatabaseFailure(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            String expectedMessage,
            Object... args
    ) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            try {
                method.invoke(target, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        });
        assertTrue(exception.getMessage().contains(expectedMessage));
    }

    private static User bidder(String id, String username) {
        Bidder bidder = new Bidder(id, username, "secret", username + "@demo.local", 1000.0);
        bidder.setRole("BIDDER");
        bidder.setFullName(username);
        return bidder;
    }
}
