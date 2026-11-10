package com.mixtape.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;

@Embeddable
public record Track(Long deezerId,
                    @Column(length = 500) String title,
                    @Column(length = 500) String artist,
                    Long artistId,
                    @Column(length = 500) String album,
                    @Column(length = 500) String cover,
                    String isrc,
                    String spotifyUri,
                    @Column(name = "deezer_rank") Integer rank,
                    @Column(name = "added_by", length = 80) String addedBy,
                    @Column(name = "added_at") Instant addedAt,
                    @Column(name = "added_by_id", length = 80) String addedById) {
    public static Track of(Long deezerId, String title, String artist, Long artistId,
                           String album, String cover, String isrc) {
        return new Track(deezerId, title, artist, artistId, album, cover, isrc, null, null, null, null, null);
    }

    public static Track probe(String title, String artist, String isrc) {
        return of(null, title, artist, null, null, null, isrc);
    }

    public Track withRank(Integer newRank) {
        return new Track(deezerId, title, artist, artistId, album, cover, isrc, spotifyUri, newRank,
                addedBy, addedAt, addedById);
    }

    public Track withSpotifyUri(String uri) {
        return new Track(deezerId, title, artist, artistId, album, cover, isrc, uri, rank,
                addedBy, addedAt, addedById);
    }

    public Track withAdded(String by, Instant at, String byId) {
        return new Track(deezerId, title, artist, artistId, album, cover, isrc, spotifyUri, rank, by, at, byId);
    }
}
