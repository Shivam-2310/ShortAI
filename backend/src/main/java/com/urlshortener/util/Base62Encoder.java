package com.urlshortener.util;

import org.springframework.stereotype.Component;

/**
 * Base62 encoder for generating short URL keys from database IDs.
 * Uses URL-safe characters: 0-9, a-z, A-Z
 * Maximum length is 10 characters, which can encode values up to 62^10
 */
@Component
public class Base62Encoder {

    private static final String BASE62_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = 62;
    private static final int MAX_LENGTH = 10;

    /**
     * Encodes a numeric ID to a Base62 string.
     *
     * @param id the database ID to encode
     * @return Base62 encoded string (max 10 characters)
     * @throws IllegalArgumentException if id is negative
     */
    public String encode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID must be non-negative");
        }

        if (id == 0) {
            return String.valueOf(BASE62_CHARS.charAt(0));
        }

        StringBuilder encoded = new StringBuilder();
        long num = id;

        while (num > 0 && encoded.length() < MAX_LENGTH) {
            int remainder = (int) (num % BASE);
            encoded.insert(0, BASE62_CHARS.charAt(remainder));
            num /= BASE;
        }

        return encoded.toString();
    }

    /**
     * Decodes a Base62 string back to a numeric ID.
     *
     * @param encoded the Base62 encoded string
     * @return the decoded numeric ID
     * @throws IllegalArgumentException if the string contains invalid characters
     */
    public long decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("Encoded string cannot be null or empty");
        }

        long id = 0;
        for (char c : encoded.toCharArray()) {
            int index = BASE62_CHARS.indexOf(c);
            if (index < 0) {
                throw new IllegalArgumentException("Invalid character in encoded string: " + c);
            }
            id = id * BASE + index;
        }

        return id;
    }

    /**
     * Validates if a string is a valid Base62 encoded key.
     *
     * @param key the string to validate
     * @return true if valid, false otherwise
     */
    public boolean isValid(String key) {
        if (key == null || key.isEmpty() || key.length() > MAX_LENGTH) {
            return false;
        }
        for (char c : key.toCharArray()) {
            if (BASE62_CHARS.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }
}

