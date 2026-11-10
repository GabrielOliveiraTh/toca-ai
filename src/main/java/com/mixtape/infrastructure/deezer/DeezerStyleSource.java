package com.mixtape.infrastructure.deezer;

import com.mixtape.application.port.StyleCandidateSource;
import com.mixtape.application.support.Parallel;
import com.mixtape.domain.style.StyleCandidate;
import com.mixtape.domain.style.StyleIntent;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Component
@Order(1)
public class DeezerStyleSource implements StyleCandidateSource {
    private static final int SEARCH_LIMIT = 12;
    private static final int MIN_PLAYLIST_TRACKS = 8;
    private static final int ERA_PLAYLISTS = 6;
    private static final int OTHER_PLAYLISTS = 8;
    private static final int PLAYLIST_TARGET = 6;
    private static final int TRACKS_PER_PLAYLIST = 30;

    private final DeezerClient client;

    DeezerStyleSource(DeezerClient client) {
        this.client = client;
    }

    @Override
    public String key() {
        return "deezer";
    }

    @Override
    public String displayName() {
        return "Deezer";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<StyleCandidate> candidates(StyleIntent intent) {
        Map<Long, DeezerClient.PlaylistRef> found = new LinkedHashMap<>();
        for (String query : intent.playlistQueries()) {
            client.searchPlaylists(query, SEARCH_LIMIT).stream()
                    .filter(playlist -> playlist.nbTracks() >= MIN_PLAYLIST_TRACKS)
                    .forEach(playlist -> found.putIfAbsent(playlist.id(), playlist));
        }
        List<DeezerClient.PlaylistRef> era = found.values().stream()
                .filter(playlist -> intent.matchesPeriod(playlist.title()))
                .limit(ERA_PLAYLISTS)
                .toList();
        List<DeezerClient.PlaylistRef> others = new ArrayList<>(found.values().stream()
                .filter(playlist -> !era.contains(playlist))
                .limit(OTHER_PLAYLISTS)
                .toList());
        Collections.shuffle(others);
        List<DeezerClient.PlaylistRef> chosen = new ArrayList<>(era);
        others.stream().limit(Math.max(2, PLAYLIST_TARGET - era.size())).forEach(chosen::add);

        Random random = new Random();
        return Parallel.flatMap(chosen, playlist -> {
            boolean eraHint = era.contains(playlist);
            int index = playlist.nbTracks() > 40 ? random.nextInt(playlist.nbTracks() - 30) : 0;
            return client.playlistTracks(playlist.id(), index, TRACKS_PER_PLAYLIST).stream()
                    .map(track -> new StyleCandidate(track.artist(), track.title(), null, null, null, null, track,
                            key(), eraHint))
                    .collect(Collectors.toList());
        });
    }
}
