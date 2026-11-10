package com.mixtape.application;

import com.mixtape.domain.Playlist;
import com.mixtape.domain.PlaylistRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Service
public class SharingService {
    private static final int TOKEN_BYTES = 18;

    private final PlaylistRepository repository;
    private final SecureRandom random = new SecureRandom();

    public SharingService(PlaylistRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public String enableShare(long id) {
        Playlist playlist = repository.require(id);
        if (playlist.getShareToken() == null) {
            playlist.setShareToken(newToken());
        }
        return playlist.getShareToken();
    }

    @Transactional
    public void disableShare(long id) {
        repository.require(id).setShareToken(null);
    }

    @Transactional(readOnly = true)
    public Optional<String> shareToken(long id) {
        return Optional.ofNullable(repository.require(id).getShareToken());
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
