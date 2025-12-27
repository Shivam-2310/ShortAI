package com.urlshortener.exception;

public class UrlNotFoundException extends RuntimeException {

    public UrlNotFoundException(String shortKey) {
        super("URL not found for key: " + shortKey);
    }
}

