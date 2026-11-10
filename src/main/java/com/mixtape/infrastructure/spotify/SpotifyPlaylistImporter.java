package com.mixtape.infrastructure.spotify;

import com.mixtape.application.TrackResolver;
import com.mixtape.application.port.PlaylistImporter;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SpotifyPlaylistImporter implements PlaylistImporter {
    private static final Pattern PLAYLIST_ID = Pattern.compile("playlist[/:]([A-Za-z0-9]+)");
    private static final int PAGE_SIZE = 50;
    private static final int MAX_TRACKS = 300;

    private final SpotifyApi api;
    private final TrackResolver resolver;

    SpotifyPlaylistImporter(SpotifyApi api, TrackResolver resolver) {
        this.api = api;
        this.resolver = resolver;
    }

    @Override
    public boolean supports(String url) {
        return true;
    }

    @Override
    public String sourceKey(String url) {
        return playlistId(url);
    }

    @Override
    public ImportedPlaylist read(String url) {
        String id = playlistId(url);
        JsonNode meta = api.get("/playlists/{id}?fields=name", id);
        List<TrackResolver.Request> requests = new ArrayList<>();
        for (int offset = 0; offset < MAX_TRACKS; offset += PAGE_SIZE) {
            JsonNode page = api.get("/playlists/{id}/items?limit=" + PAGE_SIZE + "&offset={o}", id, offset);
            JsonNode items = page.path("items");
            for (JsonNode entry : items) {
                JsonNode track = entry.hasNonNull("item") ? entry.get("item") : entry.path("track");
                if (isPlayableTrack(track)) {
                    requests.add(new TrackResolver.Request(
                            track.path("external_ids").path("isrc").asText(null),
                            track.path("name").asText(),
                            track.path("artists").path(0).path("name").asText(),
                            track.get("uri").asText()));
                }
            }
            if (items.size() < PAGE_SIZE) {
                break;
            }
        }
        return new ImportedPlaylist(meta.path("name").asText(), resolver.resolve(requests));
    }

    private static boolean isPlayableTrack(JsonNode track) {
        if (track.isMissingNode() || track.isNull() || !track.hasNonNull("uri") || track.path("is_local").asBoolean()) {
            return false;
        }
        return "track".equals(track.path("type").asText("track"));
    }

    static String playlistId(String urlOrId) {
        Matcher matcher = PLAYLIST_ID.matcher(urlOrId);
        return matcher.find() ? matcher.group(1) : urlOrId.trim();
    }
}
