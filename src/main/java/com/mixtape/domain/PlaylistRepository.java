package com.mixtape.domain;

import com.mixtape.domain.error.NotFoundException;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {
    Optional<Playlist> findByShareToken(String shareToken);

    default Playlist require(long id) {
        return findById(id).orElseThrow(() -> new NotFoundException("Playlist not found"));
    }

    default Playlist requireByShareToken(String token) {
        if (token == null || token.length() < 16) {
            throw new NotFoundException("This link is invalid or has been turned off.");
        }
        return findByShareToken(token)
                .orElseThrow(() -> new NotFoundException("This link is invalid or has been turned off."));
    }
}
