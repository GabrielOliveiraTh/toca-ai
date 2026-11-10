package com.mixtape.web.guest;

public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}
