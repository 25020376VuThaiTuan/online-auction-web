package org.example.server;

import org.example.model.Bidder;
import org.example.model.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static User bidder(String id, String username) {
        Bidder bidder = new Bidder(id, username, "secret", username + "@demo.local", 1000.0);
        bidder.setRole("BIDDER");
        bidder.setFullName(username);
        return bidder;
    }
}
