package com.urlshortener.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates random alphanumeric short URL keys.
 * Uses cryptographically secure random number generator for professional-grade randomness.
 * Generates keys of configurable length (default 6-8 characters).
 */
@Component
public class RandomKeyGenerator {

    private static final String ALPHANUMERIC_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int DEFAULT_MIN_LENGTH = 6;
    private static final int DEFAULT_MAX_LENGTH = 8;
    
    private final SecureRandom secureRandom;

    public RandomKeyGenerator() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generates a random alphanumeric key of default length (6-8 characters).
     *
     * @return a random alphanumeric string
     */
    public String generate() {
        return generate(DEFAULT_MIN_LENGTH, DEFAULT_MAX_LENGTH);
    }

    /**
     * Generates a random alphanumeric key of specified length.
     *
     * @param length the length of the key to generate
     * @return a random alphanumeric string
     */
    public String generate(int length) {
        return generate(length, length);
    }

    /**
     * Generates a random alphanumeric key with length between min and max (inclusive).
     *
     * @param minLength minimum length of the key
     * @param maxLength maximum length of the key
     * @return a random alphanumeric string
     */
    public String generate(int minLength, int maxLength) {
        if (minLength < 1 || maxLength < minLength) {
            throw new IllegalArgumentException("Invalid length parameters");
        }

        // Random length between min and max
        int length = minLength + secureRandom.nextInt(maxLength - minLength + 1);
        
        StringBuilder key = new StringBuilder(length);
        
        for (int i = 0; i < length; i++) {
            int randomIndex = secureRandom.nextInt(ALPHANUMERIC_CHARS.length());
            key.append(ALPHANUMERIC_CHARS.charAt(randomIndex));
        }
        
        return key.toString();
    }

    /**
     * Validates if a string is a valid alphanumeric key.
     *
     * @param key the string to validate
     * @return true if valid, false otherwise
     */
    public boolean isValid(String key) {
        if (key == null || key.isEmpty() || key.length() > 20) {
            return false;
        }
        for (char c : key.toCharArray()) {
            if (ALPHANUMERIC_CHARS.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }
}

