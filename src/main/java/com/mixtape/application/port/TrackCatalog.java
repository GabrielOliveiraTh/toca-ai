package com.mixtape.application.port;

import com.mixtape.domain.Track;

import java.util.List;
import java.util.Optional;

public interface TrackCatalog {
    List<Track> search(String query);

    Optional<Track> match(String isrc, String title, String artist);

    Optional<String> previewUrl(long trackId);

    Optional<byte[]> previewBytes(long trackId);
}
