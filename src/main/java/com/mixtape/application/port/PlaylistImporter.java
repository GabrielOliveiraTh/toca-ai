package com.mixtape.application.port;

import com.mixtape.domain.Track;

import java.util.List;

public interface PlaylistImporter {
    boolean supports(String url);

    String sourceKey(String url);

    ImportedPlaylist read(String url);

    record ImportedPlaylist(String name, List<Track> tracks) {
    }
}
