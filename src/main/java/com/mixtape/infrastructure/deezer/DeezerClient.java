package com.mixtape.infrastructure.deezer;

import com.mixtape.application.port.ArtistCatalog;
import com.mixtape.application.port.TrackCatalog;
import com.mixtape.application.port.TrackMetadata;
import com.mixtape.domain.Track;
import com.mixtape.domain.error.BusinessRuleException;
import com.mixtape.infrastructure.http.Http;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DeezerClient implements TrackCatalog, ArtistCatalog, TrackMetadata {
    private static final Pattern DEEZER_PLAYLIST =
            Pattern.compile("deezer\\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?playlist/(\\d+)");

    // Deezer accepts about 50 calls per 5 s; stay below it so no call is refused.
    private static final int MAX_CALLS = 42;
    private static final int RESERVED_FOR_USER = 12;
    private static final long WINDOW_MS = 5000;
    private static final int QUOTA_ERROR_CODE = 4;
    private static final int MAX_ATTEMPTS = 3;
    private static final long QUOTA_RETRY_DELAY_MS = 1200;
    private static final long MAX_THROTTLE_SLEEP_MS = 500;
    private static final int MAX_PLAYLIST_TRACKS = 300;
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PREVIEW_BYTES = 2_000_000;

    private final RestClient http = Http.client("https://api.deezer.com");
    private final RestClient audio = Http.client("https://cdn-preview.dzcdn.net");
    private final RateWindow window =
            new RateWindow(MAX_CALLS, RESERVED_FOR_USER, WINDOW_MS, System::currentTimeMillis);

    record PlaylistData(String name, List<Track> tracks) {
    }

    record PlaylistRef(long id, String title, int nbTracks) {
    }

    static boolean isDeezerUrl(String text) {
        return text != null && text.contains("deezer.");
    }

    static String playlistId(String url) {
        Matcher matcher = DEEZER_PLAYLIST.matcher(url);
        if (!matcher.find()) {
            throw new BusinessRuleException(
                    "Invalid Deezer link. Use the full playlist link (deezer.com/…/playlist/123456).");
        }
        return matcher.group(1);
    }

    private void throttle(boolean priority) throws InterruptedException {
        long wait = window.tryAcquire(priority);
        while (wait > 0) {
            Thread.sleep(Math.min(wait, MAX_THROTTLE_SLEEP_MS));
            wait = window.tryAcquire(priority);
        }
    }

    private JsonNode get(String uri, Object... vars) {
        return fetch(false, uri, vars);
    }

    private JsonNode getNow(String uri, Object... vars) {
        return fetch(true, uri, vars);
    }

    private JsonNode fetch(boolean priority, String uri, Object... vars) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                throttle(priority);
                JsonNode node = http.get().uri(uri, vars).retrieve().body(JsonNode.class);
                if (node != null && node.path("error").path("code").asInt() == QUOTA_ERROR_CODE) {
                    Thread.sleep(QUOTA_RETRY_DELAY_MS);
                    continue;
                }
                return node == null || node.has("error") ? null : node;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private List<Track> tracks(JsonNode root) {
        List<Track> out = new ArrayList<>();
        if (root != null) {
            root.path("data").forEach(node -> out.add(DeezerTrackMapper.toTrack(node)));
        }
        return out;
    }

    PlaylistData readPlaylist(String id) {
        JsonNode meta = get("/playlist/{id}", id);
        if (meta == null) {
            throw new BusinessRuleException("Deezer playlist not found or private.");
        }
        List<Track> out = new ArrayList<>();
        for (int index = 0; index < MAX_PLAYLIST_TRACKS; index += PAGE_SIZE) {
            JsonNode page = get("/playlist/{id}/tracks?limit=100&index={i}", id, index);
            if (page == null) {
                break;
            }
            JsonNode data = page.path("data");
            data.forEach(node -> out.add(DeezerTrackMapper.toTrack(node)));
            if (data.size() < PAGE_SIZE) {
                break;
            }
        }
        return new PlaylistData(meta.path("title").asText("Deezer playlist"), out);
    }

    @Override
    public Optional<Long> searchArtistId(String name) {
        JsonNode node = get("/search/artist?q={q}&limit=1", name);
        if (node == null || node.path("data").isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(node.path("data").path(0).path("id").asLong());
    }

    @Override
    public List<Album> artistAlbums(long artistId) {
        List<Album> out = new ArrayList<>();
        JsonNode node = get("/artist/{id}/albums?limit=100", artistId);
        if (node != null) {
            node.path("data").forEach(album -> out.add(new Album(
                    album.path("id").asLong(),
                    album.path("title").asText(),
                    album.path("cover_medium").asText(null),
                    releaseYear(album.path("release_date").asText("")))));
        }
        return out;
    }

    private static Integer releaseYear(String date) {
        boolean hasYear = date.length() >= 4 && date.substring(0, 4).matches("[1-9]\\d{3}");
        return hasYear ? Integer.valueOf(date.substring(0, 4)) : null;
    }

    @Override
    public List<Track> albumTracks(Album album, int limit) {
        List<Track> out = new ArrayList<>();
        JsonNode node = get("/album/{id}/tracks?limit={l}", album.id(), limit);
        if (node != null) {
            node.path("data").forEach(item -> {
                Track track = DeezerTrackMapper.toTrack(item);
                out.add(Track.of(track.deezerId(), track.title(), track.artist(), track.artistId(),
                        album.title(), album.cover(), null).withRank(track.rank()));
            });
        }
        return out;
    }

    @Override
    public Optional<OptionalDouble> bpm(long trackId) {
        JsonNode node = getNow("/track/{id}", trackId);
        if (node == null) {
            return Optional.empty();
        }
        double bpm = node.path("bpm").asDouble(0);
        return Optional.of(bpm > 0 ? OptionalDouble.of(bpm) : OptionalDouble.empty());
    }

    @Override
    public Optional<List<String>> genresOfTrack(long trackId) {
        JsonNode track = get("/track/{id}", trackId);
        if (track == null) {
            return Optional.empty();
        }
        long albumId = track.path("album").path("id").asLong(0);
        if (albumId <= 0) {
            return Optional.of(List.of());
        }
        JsonNode album = get("/album/{id}", albumId);
        if (album == null) {
            return Optional.empty();
        }
        List<String> genres = new ArrayList<>();
        album.path("genres").path("data").forEach(genre -> genres.add(genre.path("name").asText("")));
        return Optional.of(genres);
    }

    @Override
    public Optional<Integer> releaseYear(long trackId) {
        JsonNode node = get("/track/{id}", trackId);
        if (node == null) {
            return Optional.empty();
        }
        String date = node.path("release_date").asText("");
        boolean valid = date.length() >= 4 && date.substring(0, 4).matches("\\d{4}") && !date.startsWith("0000");
        return valid ? Optional.of(Integer.valueOf(date.substring(0, 4))) : Optional.empty();
    }

    List<PlaylistRef> searchPlaylists(String query, int limit) {
        List<PlaylistRef> out = new ArrayList<>();
        JsonNode node = get("/search/playlist?q={q}&limit={l}", query, limit);
        if (node != null) {
            node.path("data").forEach(playlist -> out.add(new PlaylistRef(
                    playlist.path("id").asLong(),
                    playlist.path("title").asText(),
                    playlist.path("nb_tracks").asInt())));
        }
        return out;
    }

    List<Track> playlistTracks(long playlistId, int index, int limit) {
        return tracks(get("/playlist/{id}/tracks?limit={l}&index={i}", playlistId, limit, index));
    }

    @Override
    public List<Track> search(String query) {
        return tracks(getNow("/search/track?q={q}&limit=20", query));
    }

    Optional<Track> byIsrc(String isrc) {
        if (isrc == null || isrc.isBlank()) {
            return Optional.empty();
        }
        JsonNode node = get("/track/isrc:{i}", isrc);
        return node == null ? Optional.empty() : Optional.of(DeezerTrackMapper.toTrack(node));
    }

    @Override
    public Optional<Track> match(String isrc, String title, String artist) {
        Optional<Track> byCode = byIsrc(isrc);
        if (byCode.isPresent()) {
            return byCode;
        }
        List<Track> found = tracks(get("/search/track?q={q}&limit=1",
                "artist:\"" + artist + "\" track:\"" + title + "\""));
        if (found.isEmpty()) {
            found = tracks(get("/search/track?q={q}&limit=1", artist + " " + title));
        }
        return found.stream().findFirst();
    }

    @Override
    public List<Track> artistTop(long artistId, int limit) {
        return tracks(get("/artist/{id}/top?limit={l}", artistId, limit));
    }

    @Override
    public List<Long> relatedArtists(long artistId, int limit) {
        List<Long> ids = new ArrayList<>();
        JsonNode node = get("/artist/{id}/related?limit={l}", artistId, limit);
        if (node != null) {
            node.path("data").forEach(artist -> ids.add(artist.path("id").asLong()));
        }
        return ids;
    }

    @Override
    public Optional<byte[]> previewBytes(long trackId) {
        Optional<String> url = previewUrl(trackId);
        if (url.isEmpty()) {
            return Optional.empty();
        }
        try {
            byte[] bytes = audio.get().uri(URI.create(url.get())).retrieve().body(byte[].class);
            boolean usable = bytes != null && bytes.length > 0 && bytes.length <= MAX_PREVIEW_BYTES;
            return usable ? Optional.of(bytes) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> previewUrl(long trackId) {
        JsonNode node = getNow("/track/{id}", trackId);
        if (node == null) {
            return Optional.empty();
        }
        String preview = node.path("preview").asText("");
        return preview.isBlank() ? Optional.empty() : Optional.of(preview);
    }
}
