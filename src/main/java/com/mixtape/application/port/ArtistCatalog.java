package com.mixtape.application.port;

import com.mixtape.domain.Track;

import java.util.List;
import java.util.Optional;

public interface ArtistCatalog {
    Optional<Long> searchArtistId(String name);

    List<Track> artistTop(long artistId, int limit);

    List<Long> relatedArtists(long artistId, int limit);

    List<Album> artistAlbums(long artistId);

    List<Track> albumTracks(Album album, int limit);

    record Album(long id, String title, String cover, Integer year) {
    }
}
