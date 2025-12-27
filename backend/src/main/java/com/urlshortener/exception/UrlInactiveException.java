package com.urlshortener.exception;

public class UrlInactiveException extends RuntimeException {

    public UrlInactiveException(String shortKey) {
        super("URL is inactive for key: " + shortKey);
    }
}

