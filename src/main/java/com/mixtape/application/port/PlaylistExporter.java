package com.mixtape.application.port;

import com.mixtape.domain.Track;

import java.util.List;

public interface PlaylistExporter {
    ExportResult export(String name, List<Track> tracks, String existingPlaylistId);

    record ExportResult(String url, int added, List<String> notFound, String playlistId, boolean updated) {
    }
}
