package com.mixtape.infrastructure.spotify;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.Properties;

@Component
public class SpotifyTokenStore {
    private final Path file;

    public SpotifyTokenStore(@Value("${spotify.token-file:./data/spotify-token.properties}") String path) {
        this.file = Path.of(path);
    }

    public synchronized Optional<String> loadRefreshToken() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(file)) {
            Properties properties = new Properties();
            properties.load(in);
            String token = properties.getProperty("refresh_token");
            return token == null || token.isBlank() ? Optional.empty() : Optional.of(token.trim());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public synchronized void save(String refreshToken) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Properties properties = new Properties();
            properties.setProperty("refresh_token", refreshToken);
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "Toca Aí - Spotify login (do not share)");
            }
            restrictPermissions();
        } catch (IOException e) {
            return;
        }
    }

    private void restrictPermissions() {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            return;
        }
    }

    public synchronized void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            return;
        }
    }
}
