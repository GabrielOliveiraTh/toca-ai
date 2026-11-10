package com.mixtape.infrastructure.spotify;

import com.mixtape.domain.error.BusinessRuleException;

class SpotifyApiException extends BusinessRuleException {
    private final int status;

    SpotifyApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    int status() {
        return status;
    }
}
