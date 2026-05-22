package org.example.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class CredentialHasher {
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int DEFAULT_ITERATIONS = 120_000;
    private static final int MINIMUM_ITERATIONS = 10_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private CredentialHasher() {
    }

    public static String hash(String secret) {
        String safeSecret = secret == null ? "" : secret;
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        int iterations = configuredIterations();
        byte[] hash = pbkdf2(safeSecret.toCharArray(), salt, iterations);
        return PREFIX
                + "$" + iterations
                + "$" + ENCODER.encodeToString(salt)
                + "$" + ENCODER.encodeToString(hash);
    }

    public static boolean verify(String secret, String storedHash) {
        if (!isHashed(storedHash)) {
            return false;
        }

        String[] parts = storedHash.split("\\$");
        if (parts.length != 4) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < MINIMUM_ITERATIONS) {
                return false;
            }
            byte[] salt = DECODER.decode(parts[2]);
            byte[] expectedHash = DECODER.decode(parts[3]);
            byte[] actualHash = pbkdf2((secret == null ? "" : secret).toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static boolean isHashed(String value) {
        return value != null && value.startsWith(PREFIX + "$");
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte hashedByte : hash) {
                builder.append(String.format("%02x", hashedByte));
            }
            return builder.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static int configuredIterations() {
        int configured = Integer.getInteger("auction.credentialHash.iterations", DEFAULT_ITERATIONS);
        return Math.max(MINIMUM_ITERATIONS, configured);
    }

    private static byte[] pbkdf2(char[] secret, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(secret, salt, iterations, HASH_BYTES * 8);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (java.security.NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("PBKDF2 credential hashing is unavailable.", exception);
        } finally {
            spec.clearPassword();
        }
    }
}
