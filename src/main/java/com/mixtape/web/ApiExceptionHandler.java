package com.mixtape.web;

import com.mixtape.domain.error.BusinessRuleException;
import com.mixtape.domain.error.NotFoundException;
import com.mixtape.web.guest.RateLimitException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
        return error(404, String.valueOf(e.getMessage()));
    }

    @ExceptionHandler({BusinessRuleException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException e) {
        return error(400, String.valueOf(e.getMessage()));
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, String>> externalTimeout(ResourceAccessException e) {
        return error(504, "An external service (Spotify, YouTube…) did not answer in time. Try again in a moment.");
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, String>> tooManyRequests(RateLimitException e) {
        return error(429, e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
