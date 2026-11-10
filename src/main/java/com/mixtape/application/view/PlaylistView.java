package com.mixtape.application.view;

import com.mixtape.domain.Track;

import java.util.List;

public record PlaylistView(Long id, String name, String style, String ownerName, boolean shared, String spotifyUrl,
                           int dislikeCount, List<Track> tracks, List<SourceView> sources) {
    public record SourceView(Long id, String name, String url, boolean main, List<Track> tracks) {
    }
}
