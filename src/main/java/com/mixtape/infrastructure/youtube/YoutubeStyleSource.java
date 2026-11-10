package com.mixtape.infrastructure.youtube;

import com.mixtape.application.port.StyleCandidateSource;
import com.mixtape.domain.style.StyleCandidate;
import com.mixtape.domain.style.StyleIntent;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@Order(3)
public class YoutubeStyleSource implements StyleCandidateSource {
    private static final int SEARCH_LIMIT = 8;
    private static final int MIN_PLAYLIST_SIZE = 10;
    private static final int MAX_PLAYLIST_SIZE = 300;
    private static final int PLAYLISTS_USED = 3;
    private static final int PAGES_PER_PLAYLIST = 2;
    private static final int MAX_ARTIST_LENGTH = 60;

    private final YoutubeClient client;

    YoutubeStyleSource(YoutubeClient client) {
        this.client = client;
    }

    @Override
    public String key() {
        return "youtube";
    }

    @Override
    public String displayName() {
        return "YouTube";
    }

    @Override
    public boolean available() {
        return client.configured();
    }

    @Override
    public List<StyleCandidate> candidates(StyleIntent intent) {
        if (!available()) {
            return List.of();
        }
        List<YoutubeClient.PlaylistHit> hits = client.searchPlaylists(intent.raw(), SEARCH_LIMIT);
        Map<String, Integer> counts = client.itemCounts(hits.stream().map(YoutubeClient.PlaylistHit::id).toList());
        List<YoutubeClient.PlaylistHit> chosen = hits.stream()
                .filter(hit -> counts.getOrDefault(hit.id(), 0) >= MIN_PLAYLIST_SIZE
                        && counts.get(hit.id()) <= MAX_PLAYLIST_SIZE)
                .sorted(Comparator.comparing((YoutubeClient.PlaylistHit hit) -> !intent.matchesPeriod(hit.title())))
                .limit(PLAYLISTS_USED)
                .toList();
        List<StyleCandidate> out = new ArrayList<>();
        for (YoutubeClient.PlaylistHit hit : chosen) {
            boolean eraHint = intent.matchesPeriod(hit.title());
            for (YoutubeClient.VideoTrack video : client.items(hit.id(), PAGES_PER_PLAYLIST)) {
                boolean usable = !video.artist().isBlank() && !video.title().isBlank()
                        && video.artist().length() <= MAX_ARTIST_LENGTH;
                if (usable) {
                    out.add(new StyleCandidate(video.artist(), video.title(), null, null, null, null, null,
                            key(), eraHint));
                }
            }
        }
        return out;
    }
}
