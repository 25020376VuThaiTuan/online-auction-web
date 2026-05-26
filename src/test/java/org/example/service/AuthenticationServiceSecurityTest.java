package org.example.service;

import org.example.model.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationServiceSecurityTest {

    private final AuthenticationService authService =
            AuthenticationService.getInstance();

    @Test
    void shouldRejectInvalidCredentials() {

        Optional<User> result =
                authService.authenticate(
                        "fake-user",
                        "wrong-password"
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldAuthenticateDefaultBidder() {

        Optional<User> result =
                authService.authenticate(
                        "bidder",
                        "bid123"
                );

        assertTrue(result.isPresent());
    }

    @Test
    void shouldRejectWrongPassword() {

        Optional<User> result =
                authService.authenticate(
                        "bidder",
                        "wrong-password"
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFindUserByUsername() {

        Optional<User> user =
                authService.findByUsername("bidder");

        assertTrue(user.isPresent());
    }

    @Test
    void shouldFindUserByEmail() {

        Optional<User> user =
                authService.findByEmail(
                        "bidder@demo.local"
                );

        assertTrue(user.isPresent());
    }

    @Test
    void shouldReturnAllUsersSafely() {

        List<User> users =
                authService.getAllUsers();

        assertNotNull(users);
    }

    @Test
    void shouldRejectDuplicateBidderRegistration() {

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerManualBidder(
                        "bidder",
                        "password123",
                        "another@email.com",
                        "Duplicate User"
                )
        );
    }

    @Test
    void shouldRegisterNewBidderSuccessfully() {

        String username =
                "user_" + System.currentTimeMillis();

        User user =
                authService.registerManualBidder(
                        username,
                        "password123",
                        username + "@mail.com",
                        "Test User"
                );

        assertNotNull(user);

        Optional<User> authenticated =
                authService.authenticate(
                        username,
                        "password123"
                );

        assertTrue(authenticated.isPresent());
    }

    @Test
    void shouldRegisterNewSellerSuccessfully() {

        String username =
                "seller_" + System.currentTimeMillis();

        User user =
                authService.registerManualSeller(
                        username,
                        "password123",
                        username + "@mail.com",
                        "Seller User"
                );

        assertNotNull(user);

        Optional<User> authenticated =
                authService.authenticate(
                        username,
                        "password123"
                );

        assertTrue(authenticated.isPresent());
    }
}