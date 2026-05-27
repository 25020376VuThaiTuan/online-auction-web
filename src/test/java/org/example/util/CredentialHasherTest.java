package org.example.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

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

    @Test
    void legacyPbkdf2HashesVerifyAndMalformedHashesAreRejected() {
        String previousIterations = System.getProperty("auction.credentialHash.iterations");
        try {
            System.setProperty("auction.credentialHash.iterations", "10000");
            String hash = CredentialHasher.hashLegacyPbkdf2(null);

            assertTrue(CredentialHasher.isHashed(hash));
            assertTrue(CredentialHasher.verify(null, hash));
            assertFalse(CredentialHasher.verify("wrong", hash));
            assertFalse(CredentialHasher.verify("secret", "plain-text"));
            assertFalse(CredentialHasher.verify("secret", "pbkdf2-sha256$10000$bad"));

            String[] parts = hash.split("\\$");
            String lowIterationHash = "pbkdf2-sha256$9999$" + parts[2] + "$" + parts[3];
            assertFalse(CredentialHasher.verify(null, lowIterationHash));
            assertFalse(CredentialHasher.verify(null, "pbkdf2-sha256$not-a-number$" + parts[2] + "$" + parts[3]));
        } finally {
            restoreProperty("auction.credentialHash.iterations", previousIterations);
        }
    }

    @Test
    void bcryptCostConfigurationControlsRehashDecision() throws Exception {
        String previousCost = System.getProperty("auction.credentialHash.bcryptCost");
        try {
            System.setProperty("auction.credentialHash.bcryptCost", "1");
            String minimumCostHash = CredentialHasher.hash("secret");
            assertFalse(CredentialHasher.needsRehash(minimumCostHash));

            System.setProperty("auction.credentialHash.bcryptCost", "12");
            assertTrue(CredentialHasher.needsRehash(minimumCostHash));
            assertTrue(CredentialHasher.needsRehash("plain-text"));
            assertTrue(CredentialHasher.needsRehash("$2a$xx$invalid"));
            assertFalse(CredentialHasher.verify("secret", "$2a$10$invalid"));

            System.setProperty("auction.credentialHash.bcryptCost", "99");
            assertEquals(16, configuredBcryptCost());
        } finally {
            restoreProperty("auction.credentialHash.bcryptCost", previousCost);
        }
    }

    @Test
    void sha256AndHashDetectionHandleNullsAndKnownValues() {
        assertFalse(CredentialHasher.isHashed(null));
        assertFalse(CredentialHasher.isHashed("plain"));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                CredentialHasher.sha256Hex(null));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                CredentialHasher.sha256Hex("abc"));
    }

    @Test
    void privateConstructorCanBeInvokedForCoverage() throws Exception {
        Constructor<CredentialHasher> constructor = CredentialHasher.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertNotNull(constructor.newInstance());
    }

    private static void restoreProperty(String propertyName, String value) {
        if (value == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, value);
        }
    }

    private static int configuredBcryptCost() throws Exception {
        Method method = CredentialHasher.class.getDeclaredMethod("configuredBcryptCost");
        method.setAccessible(true);
        return (Integer) method.invoke(null);
    }
}
