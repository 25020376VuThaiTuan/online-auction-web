package org.example.service;

import org.example.model.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthSessionSecurityTest {

    private final AuthenticationService authService =
            AuthenticationService.getInstance();

    @Test
    void shouldCreateValidAuthenticationSession() {

        Optional<User> authenticated =
                authService.authenticate(
                        "bidder",
                        "bid123"
                );

        assertTrue(authenticated.isPresent());

        User user = authenticated.get();

        assertNotNull(user.getId());
        assertNotNull(user.getUsername());
        assertNotNull(user.getRole());
    }

    @Test
    void shouldRejectSessionForInvalidPassword() {

        Optional<User> authenticated =
                authService.authenticate(
                        "bidder",
                        "wrong-password"
                );

        assertTrue(authenticated.isEmpty());
    }

    @Test
    void shouldRejectSessionForUnknownUser() {

        Optional<User> authenticated =
                authService.authenticate(
                        "unknown-user",
                        "password123"
                );

        assertTrue(authenticated.isEmpty());
    }

    @Test
    void shouldMaintainCorrectRoleAfterAuthentication() {

        Optional<User> authenticated =
                authService.authenticate(
                        "admin",
                        "admin123"
                );

        assertTrue(authenticated.isPresent());

        assertEquals(
                "ADMIN",
                authenticated.get().getRole()
        );
    }

    @Test
    void shouldAuthenticateSellerSession() {

        Optional<User> authenticated =
                authService.authenticate(
                        "seller",
                        "sell123"
                );

        assertTrue(authenticated.isPresent());

        assertEquals(
                "SELLER",
                authenticated.get().getRole()
        );
    }

    @Test
    void shouldAuthenticateBidderSession() {

        Optional<User> authenticated =
                authService.authenticate(
                        "bidder",
                        "bid123"
                );

        assertTrue(authenticated.isPresent());

        assertEquals(
                "BIDDER",
                authenticated.get().getRole()
        );
    }

    @Test
    void shouldRejectEmptyCredentials() {

        Optional<User> authenticated =
                authService.authenticate(
                        "",
                        ""
                );

        assertTrue(authenticated.isEmpty());
    }

    @Test
    void shouldRejectNullCredentials() {

        Optional<User> authenticated =
                authService.authenticate(
                        null,
                        null
                );

        assertTrue(authenticated.isEmpty());
    }
}