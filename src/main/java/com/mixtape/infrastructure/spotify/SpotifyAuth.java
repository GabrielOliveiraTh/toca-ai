package com.mixtape.infrastructure.spotify;

import com.mixtape.application.port.MusicAccount;
import com.mixtape.domain.error.BusinessRuleException;
import com.mixtape.infrastructure.http.Http;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Component
public class SpotifyAuth implements MusicAccount {
    private static final String DEFAULT_ACCOUNTS_BASE = "https://accounts.spotify.com";
    private static final String AUTHORIZE_URL = "https://accounts.spotify.com/authorize";
    private static final String SCOPES =
            "playlist-read-private playlist-read-collaborative playlist-modify-private playlist-modify-public";
    private static final int EXPIRY_MARGIN_SECONDS = 60;

    private final String clientId;
    private final String redirectUri;
    private final RestClient accounts;
    private final SpotifyTokenStore store;
    private final SecureRandom random = new SecureRandom();

    private String pendingState;
    private String pendingVerifier;
    private volatile String accessToken;
    private volatile String refreshToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    SpotifyAuth(String clientId, String redirectUri, SpotifyTokenStore store) {
        this(clientId, redirectUri, store, DEFAULT_ACCOUNTS_BASE);
    }

    @Autowired
    public SpotifyAuth(@Value("${spotify.client-id}") String clientId,
                       @Value("${spotify.redirect-uri}") String redirectUri,
                       SpotifyTokenStore store,
                       @Value("${spotify.accounts-base:" + DEFAULT_ACCOUNTS_BASE + "}") String accountsBase) {
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.store = store;
        this.accounts = Http.client(accountsBase);
        if (configured()) {
            this.refreshToken = store.loadRefreshToken().orElse(null);
        }
    }

    @Override
    public boolean configured() {
        return clientId != null && !clientId.isBlank();
    }

    @Override
    public boolean connected() {
        return accessToken != null || refreshToken != null;
    }

    @Override
    public synchronized void disconnect() {
        accessToken = null;
        refreshToken = null;
        expiresAt = Instant.EPOCH;
        store.clear();
    }

    @Override
    public synchronized String loginUrl() {
        if (!configured()) {
            throw new BusinessRuleException("Set SPOTIFY_CLIENT_ID (see the README) to connect to Spotify.");
        }
        pendingVerifier = randomString(64);
        pendingState = randomString(16);
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", SCOPES)
                .queryParam("state", pendingState)
                .queryParam("code_challenge_method", "S256")
                .queryParam("code_challenge", challenge(pendingVerifier))
                .build().encode().toUriString();
    }

    @Override
    public synchronized void handleCallback(String code, String state) {
        if (pendingState == null || !pendingState.equals(state)) {
            throw new BusinessRuleException("Invalid state");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", clientId);
        form.add("code_verifier", pendingVerifier);
        storeToken(requestToken(form));
        pendingState = null;
        pendingVerifier = null;
    }

    // synchronized: the same monitor guards the login state and the token renewal.
    synchronized String bearer() {
        if (!connected()) {
            throw new BusinessRuleException("Connect your Spotify account first.");
        }
        if (accessToken == null || Instant.now().isAfter(expiresAt)) {
            renew();
        }
        return "Bearer " + accessToken;
    }

    private void renew() {
        if (refreshToken == null) {
            disconnect();
            throw new BusinessRuleException("Spotify session expired, connect again.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        try {
            storeToken(requestToken(form));
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 400 || status == 401) {
                disconnect();
                throw new BusinessRuleException("Your Spotify session expired. Connect again.");
            }
            throw new BusinessRuleException("Could not renew the Spotify login right now (" + status + ").");
        }
    }

    private JsonNode requestToken(MultiValueMap<String, String> form) {
        return accounts.post().uri("/api/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
    }

    private void storeToken(JsonNode token) {
        accessToken = token.path("access_token").asText();
        if (token.hasNonNull("refresh_token")) {
            refreshToken = token.get("refresh_token").asText();
            store.save(refreshToken);
        }
        expiresAt = Instant.now().plusSeconds(token.path("expires_in").asLong(3600) - EXPIRY_MARGIN_SECONDS);
    }

    private String randomString(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
    }

    private String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
