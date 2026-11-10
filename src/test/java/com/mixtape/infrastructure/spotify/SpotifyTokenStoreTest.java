package com.mixtape.infrastructure.spotify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotifyTokenStoreTest {
    @TempDir Path dir;

    @Test
    void savedTokenSurvivesANewInstanceLikeAnAppRestart() {
        Path file = dir.resolve("sub/spotify-token.properties");
        new SpotifyTokenStore(file.toString()).save("refresh-abc_123");
        assertEquals("refresh-abc_123", new SpotifyTokenStore(file.toString()).loadRefreshToken().orElseThrow());
    }

    @Test
    void missingOrEmptyFileMeansNotConnected() throws Exception {
        Path file = dir.resolve("none.properties");
        assertTrue(new SpotifyTokenStore(file.toString()).loadRefreshToken().isEmpty());
        Files.writeString(file, "refresh_token=\n");
        assertTrue(new SpotifyTokenStore(file.toString()).loadRefreshToken().isEmpty());
    }

    @Test
    void clearForgetsTheLogin() {
        Path file = dir.resolve("t.properties");
        SpotifyTokenStore s = new SpotifyTokenStore(file.toString());
        s.save("x");
        s.clear();
        assertFalse(Files.exists(file));
        assertTrue(s.loadRefreshToken().isEmpty());
    }

    @Test
    void clientStartsConnectedWhenATokenWasSavedAndDisconnectsForgettingIt() {
        Path file = dir.resolve("c.properties");
        SpotifyTokenStore store = new SpotifyTokenStore(file.toString());
        store.save("r1");
        SpotifyAuth c = new SpotifyAuth("client-id", "http://127.0.0.1:8080/cb", store);
        assertTrue(c.connected());
        c.disconnect();
        assertFalse(c.connected());
        assertFalse(Files.exists(file));
        assertFalse(new SpotifyAuth("client-id", "http://127.0.0.1:8080/cb", store).connected());
    }

    @Test
    void clientWithoutClientIdIgnoresAStoredToken() {
        Path file = dir.resolve("d.properties");
        SpotifyTokenStore store = new SpotifyTokenStore(file.toString());
        store.save("r1");
        assertFalse(new SpotifyAuth("", "http://127.0.0.1:8080/cb", store).connected());
    }
}
