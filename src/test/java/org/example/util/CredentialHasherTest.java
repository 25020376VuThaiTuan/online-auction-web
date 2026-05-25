package org.example.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CredentialHasherTest {

    @Test
    void shouldHashPassword() {

        String raw = "123456";

        String hash = CredentialHasher.hash(raw);

        assertNotNull(hash);

        assertNotEquals(raw, hash);
    }

    @Test
    void shouldVerifyPassword() {

        String raw = "admin123";

        String hash = CredentialHasher.hash(raw);

        boolean result =
                CredentialHasher.verify(raw, hash);

        assertTrue(result);
    }

    @Test
    void shouldRejectWrongPassword() {

        String hash =
                CredentialHasher.hash("password");

        boolean result =
                CredentialHasher.verify(
                        "wrong",
                        hash
                );

        assertFalse(result);
    }
}