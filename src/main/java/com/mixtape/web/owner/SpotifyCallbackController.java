package com.mixtape.web.owner;

import com.mixtape.application.port.MusicAccount;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpotifyCallbackController {
    private final MusicAccount account;

    public SpotifyCallbackController(MusicAccount account) {
        this.account = account;
    }

    @GetMapping("/auth/spotify/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String target = "/?spotify=error";
        if (error == null && code != null) {
            try {
                account.handleCallback(code, state);
                target = "/?spotify=ok";
            } catch (RuntimeException e) {
                target = "/?spotify=error";
            }
        }
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, target).build();
    }
}
