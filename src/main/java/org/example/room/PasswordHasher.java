package org.example.room;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * SHA-256 password hashing with random salt. Never log plaintext passwords.
 */
public final class PasswordHasher {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int SALT_LENGTH = 16;
    private static final Base64.Encoder B64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder B64_DECODER = Base64.getDecoder();

    private PasswordHasher() {
    }

    public static SaltedHash hash(String password) {
        byte[] salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        byte[] digest = digest(salt, password);
        return new SaltedHash(B64_ENCODER.encodeToString(salt), B64_ENCODER.encodeToString(digest));
    }

    public static boolean matches(String password, String saltB64, String hashB64) {
        byte[] salt = B64_DECODER.decode(saltB64);
        byte[] expected = B64_DECODER.decode(hashB64);
        byte[] actual = digest(salt, password);
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] digest(byte[] salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(password.getBytes(StandardCharsets.UTF_8));
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static final class SaltedHash {
        private final String saltB64;
        private final String hashB64;

        public SaltedHash(String saltB64, String hashB64) {
            this.saltB64 = saltB64;
            this.hashB64 = hashB64;
        }

        public String getSaltB64() {
            return saltB64;
        }

        public String getHashB64() {
            return hashB64;
        }
    }
}
