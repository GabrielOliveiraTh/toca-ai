package com.mixtape.infrastructure.spotify;

import com.mixtape.application.port.PlaylistExporter;
import com.mixtape.domain.Track;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SpotifyExporter implements PlaylistExporter {
    private static final String PLAYLIST_URL = "https://open.spotify.com/playlist/";
    private static final int BLOCK_SIZE = 100;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_FORBIDDEN = 403;

    private final SpotifyApi api;

    SpotifyExporter(SpotifyApi api) {
        this.api = api;
    }

    @Override
    public ExportResult export(String name, List<Track> tracks, String existingPlaylistId) {
        List<String> uris = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (Track track : tracks) {
            String uri = track.spotifyUri() != null ? track.spotifyUri() : findUri(track);
            if (uri == null) {
                notFound.add(track.artist() + " - " + track.title());
            } else {
                uris.add(uri);
            }
        }

        if (existingPlaylistId != null && !existingPlaylistId.isBlank()) {
            try {
                fill(existingPlaylistId, uris, true);
                rename(existingPlaylistId, name);
                return new ExportResult(PLAYLIST_URL + existingPlaylistId, uris.size(), notFound,
                        existingPlaylistId, true);
            } catch (SpotifyApiException e) {
                boolean playlistGone = e.status() == HTTP_NOT_FOUND || e.status() == HTTP_FORBIDDEN;
                if (!playlistGone) {
                    throw e;
                }
            }
        }

        JsonNode created = api.post("/me/playlists",
                Map.of("name", name, "public", false, "description", "Created with Toca Aí"));
        String id = created.path("id").asText();
        fill(id, uris, false);
        return new ExportResult(created.path("external_urls").path("spotify").asText(), uris.size(), notFound, id,
                false);
    }

    private void rename(String playlistId, String name) {
        try {
            api.put("/playlists/{id}", Map.of("name", name), playlistId);
        } catch (SpotifyApiException ignored) {
        }
    }

    private void fill(String playlistId, List<String> uris, boolean replace) {
        if (replace) {
            api.put("/playlists/{id}/items", Map.of("uris", uris.subList(0, Math.min(BLOCK_SIZE, uris.size()))),
                    playlistId);
        }
        for (int start = replace ? BLOCK_SIZE : 0; start < uris.size(); start += BLOCK_SIZE) {
            api.post("/playlists/{id}/items",
                    Map.of("uris", uris.subList(start, Math.min(start + BLOCK_SIZE, uris.size()))), playlistId);
        }
    }

    private String findUri(Track track) {
        List<String> queries = new ArrayList<>();
        if (track.isrc() != null && !track.isrc().isBlank()) {
            queries.add("isrc:" + track.isrc());
        }
        queries.add("track:" + track.title() + " artist:" + track.artist());
        for (String query : queries) {
            JsonNode result = api.get("/search?q={q}&type=track&limit=1", query);
            JsonNode first = result.path("tracks").path("items").path(0);
            if (first.hasNonNull("uri")) {
                return first.get("uri").asText();
            }
        }
        return null;
    }
}
