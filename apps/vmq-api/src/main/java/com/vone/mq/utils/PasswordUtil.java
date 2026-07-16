package com.vone.mq.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class PasswordUtil {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);

    private PasswordUtil() {}

    public static String hash(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new IllegalArgumentException("password must not be empty");
        }
        return ENCODER.encode(plaintext);
    }

    public static boolean isHash(String stored) {
        return stored != null && (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$"));
    }

    public static String hashIfNecessary(String value) {
        if (isHash(value)) {
            return value;
        }
        return hash(value);
    }

    public static boolean isDefaultPassword(String stored) {
        return SigningUtil.constantTimeEquals("admin", stored);
    }

    public static boolean matches(String plaintext, String stored) {
        if (plaintext == null || stored == null) return false;
        if (isHash(stored)) {
            return ENCODER.matches(plaintext, stored);
        }
        return SigningUtil.constantTimeEquals(plaintext, stored);
    }
}
