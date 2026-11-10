package com.mixtape.infrastructure.deezer;

import com.mixtape.application.port.PlaylistImporter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class DeezerPlaylistImporter implements PlaylistImporter {
    private final DeezerClient client;

    DeezerPlaylistImporter(DeezerClient client) {
        this.client = client;
    }

    @Override
    public boolean supports(String url) {
        return DeezerClient.isDeezerUrl(url);
    }

    @Override
    public String sourceKey(String url) {
        return "dz:" + DeezerClient.playlistId(url);
    }

    @Override
    public ImportedPlaylist read(String url) {
        DeezerClient.PlaylistData data = client.readPlaylist(DeezerClient.playlistId(url));
        return new ImportedPlaylist(data.name(), data.tracks());
    }
}
