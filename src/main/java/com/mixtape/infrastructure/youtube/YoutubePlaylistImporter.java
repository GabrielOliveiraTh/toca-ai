package com.mixtape.infrastructure.youtube;

import com.mixtape.application.TrackResolver;
import com.mixtape.application.port.PlaylistImporter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(2)
public class YoutubePlaylistImporter implements PlaylistImporter {
    private final YoutubeClient client;
    private final TrackResolver resolver;

    YoutubePlaylistImporter(YoutubeClient client, TrackResolver resolver) {
        this.client = client;
        this.resolver = resolver;
    }

    @Override
    public boolean supports(String url) {
        return YoutubeClient.isYoutubeUrl(url);
    }

    @Override
    public String sourceKey(String url) {
        return "yt:" + YoutubeClient.playlistId(url);
    }

    @Override
    public ImportedPlaylist read(String url) {
        YoutubeClient.PlaylistData data = client.readPlaylist(YoutubeClient.playlistId(url));
        List<TrackResolver.Request> requests = data.tracks().stream()
                .map(video -> TrackResolver.Request.of(null, video.title(), video.artist()))
                .toList();
        return new ImportedPlaylist(data.name(), resolver.resolve(requests));
    }
}
