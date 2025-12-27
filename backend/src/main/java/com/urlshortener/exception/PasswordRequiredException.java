package com.urlshortener.exception;

public class PasswordRequiredException extends RuntimeException {

    public PasswordRequiredException(String shortKey) {
        super("Password required for URL: " + shortKey);
    }
}

