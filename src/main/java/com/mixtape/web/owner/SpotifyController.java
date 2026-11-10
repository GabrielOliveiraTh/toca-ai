package com.mixtape.web.owner;

import com.mixtape.application.port.MusicAccount;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SpotifyController {
    private final MusicAccount account;

    public SpotifyController(MusicAccount account) {
        this.account = account;
    }

    public record SpotifyStatus(boolean configured, boolean connected) {
    }

    public record LoginUrl(String url) {
    }

    public record ConnectionState(boolean connected) {
    }

    @GetMapping("/spotify/status")
    public SpotifyStatus status() {
        return new SpotifyStatus(account.configured(), account.connected());
    }

    @GetMapping("/spotify/login")
    public LoginUrl login() {
        return new LoginUrl(account.loginUrl());
    }

    @PostMapping("/spotify/logout")
    public ConnectionState logout() {
        account.disconnect();
        return new ConnectionState(false);
    }
}
