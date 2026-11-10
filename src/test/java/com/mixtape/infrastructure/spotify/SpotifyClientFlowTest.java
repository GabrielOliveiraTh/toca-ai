package com.mixtape.infrastructure.spotify;

import com.mixtape.application.port.PlaylistExporter;
import com.mixtape.domain.Track;
import com.mixtape.domain.error.BusinessRuleException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotifyClientFlowTest {
    record Req(String method, String path, String auth, String body) {}

    @TempDir Path dir;
    HttpServer server;
    final List<Req> requests = new CopyOnWriteArrayList<>();
    volatile int putItemsStatus = 200;
    volatile int tokenStatus = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = ex.getRequestURI().getPath();
        requests.add(new Req(ex.getRequestMethod(), path, ex.getRequestHeaders().getFirst("Authorization"), body));
        int status = 200;
        String json = "{}";
        if (path.equals("/api/token")) {
            status = tokenStatus;
            json = tokenStatus == 200 ? "{\"access_token\":\"AT\",\"expires_in\":3600}" : "{\"error\":\"invalid_grant\"}";
        } else if (path.equals("/v1/me/playlists")) {
            json = "{\"id\":\"NEW\",\"external_urls\":{\"spotify\":\"https://open.spotify.com/playlist/NEW\"}}";
        } else if (path.matches("/v1/playlists/[^/]+/items") && ex.getRequestMethod().equals("PUT")) {
            status = putItemsStatus;
            json = putItemsStatus == 200 ? "{\"snapshot_id\":\"s\"}" : "{\"error\":{\"status\":" + putItemsStatus + "}}";
        }
        byte[] out = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    private record Spotify(SpotifyAuth auth, SpotifyExporter exporter) {
        boolean connected() {
            return auth.connected();
        }

        PlaylistExporter.ExportResult export(String name, List<Track> tracks, String existingPlaylistId) {
            return exporter.export(name, tracks, existingPlaylistId);
        }
    }

    private Spotify client(String savedRefresh) {
        SpotifyTokenStore store = new SpotifyTokenStore(dir.resolve("tok.properties").toString());
        if (savedRefresh != null) {
            store.save(savedRefresh);
        }
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        SpotifyAuth auth = new SpotifyAuth("cid", "http://127.0.0.1:8080/cb", store, base);
        SpotifyApi api = new SpotifyApi(auth, base + "/v1");
        return new Spotify(auth, new SpotifyExporter(api));
    }

    private static List<Track> tracks(int n) {
        return IntStream.rangeClosed(1, n).mapToObj(i ->
                Track.of((long) i, "T" + i, "A", 1L, null, null, null).withSpotifyUri("spotify:track:" + i)).toList();
    }

    private List<Req> to(String method, String pathRegex) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().matches(pathRegex)).toList();
    }

    @Test
    void savedRefreshTokenIsExchangedForAnAccessTokenOnFirstUse() {
        Spotify c = client("saved-refresh");
        assertTrue(c.connected());
        assertTrue(to("POST", "/api/token").isEmpty());

        c.export("Minha", tracks(2), null);

        Req token = to("POST", "/api/token").get(0);
        assertTrue(token.body().contains("grant_type=refresh_token"));
        assertTrue(token.body().contains("refresh_token=saved-refresh"));
        assertEquals("Bearer AT", to("POST", "/v1/me/playlists").get(0).auth());
        assertEquals(1, to("POST", "/api/token").size());
    }

    @Test
    void revokedRefreshTokenDisconnectsAndForgetsTheSavedLogin() {
        tokenStatus = 400;
        Spotify c = client("revoked");
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> c.export("X", tracks(1), null));
        assertTrue(e.getMessage().contains("Connect again"));
        assertFalse(c.connected());
        assertFalse(Files.exists(dir.resolve("tok.properties")));
    }

    @Test
    void firstExportCreatesThePlaylistAndFillsItInBlocksOf100() {
        Spotify c = client("r");
        PlaylistExporter.ExportResult r = c.export("New", tracks(250), null);

        assertFalse(r.updated());
        assertEquals("NEW", r.playlistId());
        assertEquals(250, r.added());
        assertEquals(1, to("POST", "/v1/me/playlists").size());
        assertEquals(3, to("POST", "/v1/playlists/NEW/items").size());
        assertTrue(to("PUT", "/v1/playlists/NEW/items").isEmpty());
    }

    @Test
    void secondExportUpdatesTheSamePlaylistInsteadOfCreatingAnother() {
        Spotify c = client("r");
        PlaylistExporter.ExportResult r = c.export("Renomeada", tracks(150), "EXISTING");

        assertTrue(r.updated());
        assertEquals("EXISTING", r.playlistId());
        assertEquals("https://open.spotify.com/playlist/EXISTING", r.url());
        assertTrue(to("POST", "/v1/me/playlists").isEmpty());
        assertEquals(1, to("PUT", "/v1/playlists/EXISTING/items").size());
        assertEquals(1, to("POST", "/v1/playlists/EXISTING/items").size());
        assertTrue(to("PUT", "/v1/playlists/EXISTING/items").get(0).body().contains("spotify:track:100"));
        assertFalse(to("PUT", "/v1/playlists/EXISTING/items").get(0).body().contains("spotify:track:101"));
        assertEquals(1, to("PUT", "/v1/playlists/EXISTING").size());
        assertTrue(to("PUT", "/v1/playlists/EXISTING").get(0).body().contains("Renomeada"));
    }

    @Test
    void ifThePlaylistWasDeletedOnSpotifyAFreshOneIsCreated() {
        putItemsStatus = 404;
        Spotify c = client("r");
        PlaylistExporter.ExportResult r = c.export("Volta", tracks(3), "GONE");

        assertFalse(r.updated());
        assertEquals("NEW", r.playlistId());
        assertEquals(1, to("POST", "/v1/me/playlists").size());
        assertEquals(1, to("POST", "/v1/playlists/NEW/items").size());
    }

    @Test
    void otherFailuresWhileUpdatingAreReportedInsteadOfDuplicatingThePlaylist() {
        putItemsStatus = 500;
        Spotify c = client("r");
        assertThrows(BusinessRuleException.class, () -> c.export("X", tracks(2), "EXISTING"));
        assertTrue(to("POST", "/v1/me/playlists").isEmpty());
    }

    @Test
    void exportingZeroTracksToAnExistingPlaylistClearsIt() {
        Spotify c = client("r");
        List<Track> none = new ArrayList<>();
        PlaylistExporter.ExportResult r = c.export("Vazia", none, "EXISTING");
        assertTrue(r.updated());
        assertEquals("{\"uris\":[]}", to("PUT", "/v1/playlists/EXISTING/items").get(0).body());
    }
}
