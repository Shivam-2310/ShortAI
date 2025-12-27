package com.urlshortener.exception;

public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException(String shortKey) {
        super("Invalid password for URL: " + shortKey);
    }
}

