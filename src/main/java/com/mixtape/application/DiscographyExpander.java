package com.mixtape.application;

import com.mixtape.application.port.ArtistCatalog;
import com.mixtape.domain.Track;
import com.mixtape.domain.TrackMatcher;
import com.mixtape.domain.style.StyleCandidate;
import com.mixtape.domain.style.StyleIntent;
import com.mixtape.domain.style.StyleScorer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Component
public class DiscographyExpander {
    private static final Logger log = LoggerFactory.getLogger(DiscographyExpander.class);
    private static final int TOP_ARTISTS = 16;
    private static final int ERA_ARTISTS = 4;
    private static final int RELATED_PER_ARTIST = 10;
    private static final int RELATED_ARTISTS = 14;
    private static final int ALBUMS_PER_ARTIST = 3;
    private static final int TRACKS_PER_ALBUM = 8;
    private static final int ALBUM_TRACK_FETCH = 30;
    private static final int ERA_WEIGHT = 4;

    private final ArtistCatalog artists;
    private final StyleWorkers workers;

    public DiscographyExpander(ArtistCatalog artists, StyleWorkers workers) {
        this.artists = artists;
        this.workers = workers;
    }

    private record Discography(long artistId, int albumsInRange, List<StyleCandidate> candidates) {
    }

    public List<StyleCandidate> expand(List<StyleCandidate> all, StyleIntent intent) {
        Map<String, Integer> frequency = new HashMap<>();
        Map<String, String> display = new HashMap<>();
        Map<String, Long> knownIds = new HashMap<>();
        for (StyleCandidate candidate : all) {
            String key = TrackMatcher.norm(candidate.artist());
            if (key.isEmpty()) {
                continue;
            }
            int weight = 1;
            if (candidate.year() != null && StyleScorer.inRange(candidate.year(), intent)) {
                weight += ERA_WEIGHT;
            }
            if (candidate.eraHint()) {
                weight += ERA_WEIGHT;
            }
            frequency.merge(key, weight, Integer::sum);
            display.putIfAbsent(key, candidate.artist());
            Track track = candidate.catalogTrack();
            if (track != null && track.artistId() != null && track.artistId() > 0) {
                knownIds.putIfAbsent(key, track.artistId());
            }
        }
        List<String> top = frequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_ARTISTS)
                .map(Map.Entry::getKey)
                .toList();

        log.info("Discography: candidate artists {}", top.stream().map(display::get).toList());
        List<Discography> firstWave = workers.runJobs(top.stream()
                .<Callable<Discography>>map(key -> () -> {
                    long id = knownIds.containsKey(key)
                            ? knownIds.get(key)
                            : artists.searchArtistId(display.get(key)).orElse(0L);
                    return id == 0 ? null : discographyOf(id, intent);
                })
                .toList());

        log.info("Discography: first wave answered {} of {} artists; with albums of the era: {}",
                firstWave.size(), top.size(), firstWave.stream().filter(d -> d.albumsInRange() > 0).count());
        Set<Long> done = firstWave.stream().map(Discography::artistId).collect(Collectors.toSet());
        List<Discography> eraArtists = firstWave.stream()
                .filter(d -> d.albumsInRange() > 0)
                .sorted(Comparator.comparingInt(Discography::albumsInRange).reversed())
                .limit(ERA_ARTISTS)
                .toList();
        Set<Long> related = new LinkedHashSet<>();
        List<List<Long>> relatedLists = workers.runJobs(eraArtists.stream()
                .<Callable<List<Long>>>map(d -> () -> artists.relatedArtists(d.artistId(), RELATED_PER_ARTIST))
                .toList());
        for (List<Long> ids : relatedLists) {
            for (Long id : ids) {
                if (!done.contains(id)) {
                    related.add(id);
                }
            }
        }
        List<Discography> secondWave = workers.runJobs(related.stream()
                .limit(RELATED_ARTISTS)
                .<Callable<Discography>>map(id -> () -> discographyOf(id, intent))
                .toList());

        List<StyleCandidate> out = new ArrayList<>();
        firstWave.forEach(d -> out.addAll(d.candidates()));
        secondWave.forEach(d -> out.addAll(d.candidates()));
        return out;
    }

    private Discography discographyOf(long artistId, StyleIntent intent) {
        List<ArtistCatalog.Album> albums = new ArrayList<>(artists.artistAlbums(artistId).stream()
                .filter(album -> album.year() != null && StyleScorer.inRange(album.year(), intent))
                .toList());
        Collections.shuffle(albums);
        List<StyleCandidate> out = new ArrayList<>();
        for (ArtistCatalog.Album album : albums.stream().limit(ALBUMS_PER_ARTIST).toList()) {
            List<Track> tracks = new ArrayList<>(artists.albumTracks(album, ALBUM_TRACK_FETCH));
            Collections.shuffle(tracks);
            tracks.stream().limit(TRACKS_PER_ALBUM).forEach(track -> out.add(new StyleCandidate(
                    track.artist(), track.title(), album.year(), null, null, null, track,
                    StyleScorer.DISCOGRAPHY_SOURCE, false)));
        }
        return new Discography(artistId, albums.size(), out);
    }
}
