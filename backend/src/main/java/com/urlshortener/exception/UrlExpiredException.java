package com.urlshortener.exception;

public class UrlExpiredException extends RuntimeException {

    public UrlExpiredException(String shortKey) {
        super("URL has expired for key: " + shortKey);
    }
}

