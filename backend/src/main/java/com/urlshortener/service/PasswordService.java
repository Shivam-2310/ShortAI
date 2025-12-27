package com.urlshortener.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service for password hashing and verification.
 * Uses BCrypt for secure password storage.
 */
@Service
public class PasswordService {

    private final PasswordEncoder passwordEncoder;

    public PasswordService() {
        this.passwordEncoder = new BCryptPasswordEncoder(12);
    }

    /**
     * Hash a password using BCrypt.
     *
     * @param rawPassword The plain text password
     * @return BCrypt hash of the password
     */
    public String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Verify a password against a hash.
     *
     * @param rawPassword The plain text password to verify
     * @param hashedPassword The BCrypt hash to verify against
     * @return true if the password matches
     */
    public boolean verifyPassword(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }

    /**
     * Check if a password meets minimum requirements.
     *
     * @param password The password to validate
     * @return true if password meets requirements
     */
    public boolean isValidPassword(String password) {
        if (password == null || password.length() < 4) {
            return false;
        }
        // Allow any password with 4+ characters for URL protection
        // This is intentionally permissive for user convenience
        return password.length() <= 128;
    }
}

