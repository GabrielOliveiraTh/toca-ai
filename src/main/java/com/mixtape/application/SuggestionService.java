package com.mixtape.application;

import com.mixtape.application.port.ArtistCatalog;
import com.mixtape.application.support.Parallel;
import com.mixtape.domain.CommonTrack;
import com.mixtape.domain.Dislike;
import com.mixtape.domain.Playlist;
import com.mixtape.domain.PlaylistRepository;
import com.mixtape.domain.PlaylistTrack;
import com.mixtape.domain.SuggestionFilters;
import com.mixtape.domain.Track;
import com.mixtape.domain.TrackMatcher;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class SuggestionService {
    private static final int LIMIT = 24;
    private static final int BLOCKING_DISLIKES = 2;
    private static final int TOP_ARTISTS = 4;
    private static final int RELATED_ARTISTS_FETCHED = 8;
    private static final int RELATED_ARTISTS_USED = 3;
    private static final long COMMON_TRACK_WEIGHT = 3L;

    private final PlaylistRepository repository;
    private final ArtistCatalog artistCatalog;
    private final StyleSuggestionService styles;
    private final GenreFilter genres;

    public SuggestionService(PlaylistRepository repository, ArtistCatalog artistCatalog,
                             StyleSuggestionService styles, GenreFilter genres) {
        this.repository = repository;
        this.artistCatalog = artistCatalog;
        this.styles = styles;
        this.genres = genres;
    }

    @Transactional(readOnly = true)
    public List<Track> suggest(long playlistId, SuggestionFilters filters) {
        Playlist playlist = repository.require(playlistId);
        List<Track> mine = playlist.getTracks().stream().map(PlaylistTrack::toTrack).toList();
        List<Track> seeds = new ArrayList<>(mine);
        playlist.getSources().forEach(source -> seeds.addAll(source.getTracks()));

        String style = playlist.getStyle();
        boolean hasStyle = style != null && !style.isBlank();
        if (seeds.isEmpty() && !hasStyle) {
            return List.of();
        }

        Set<String> known = TrackMatcher.keysOf(seeds);
        Predicate<Track> accept = acceptance(playlist, filters);
        List<Track> byStyle = hasStyle ? styles.tracksFor(style, accept) : List.of();
        List<Track> byArtists = seeds.isEmpty()
                ? List.of()
                : artistPool(playlist, mine, seeds).stream().filter(accept).toList();

        List<Track> fromStyle = new ArrayList<>(unknownTracks(byStyle, known));
        List<Track> fromArtists = new ArrayList<>(unknownTracks(byArtists, known));
        Collections.shuffle(fromStyle);
        Collections.shuffle(fromArtists);
        fromStyle = new ArrayList<>(genres.apply(fromStyle, filters.excludedGenres()));
        fromArtists = new ArrayList<>(genres.apply(fromArtists, filters.excludedGenres()));
        return interleave(fromStyle, fromArtists);
    }

    private static List<Track> unknownTracks(List<Track> tracks, Set<String> known) {
        return tracks.stream().filter(track -> !TrackMatcher.containedIn(track, known)).toList();
    }

    public static Predicate<Track> acceptance(Playlist playlist, SuggestionFilters filters) {
        List<Track> disliked = playlist.getDislikes().stream().map(Dislike::toTrack).toList();
        Set<String> dislikedKeys = TrackMatcher.keysOf(disliked);
        Map<Long, Long> perArtist = disliked.stream()
                .filter(track -> track.artistId() != null && track.artistId() > 0)
                .collect(Collectors.groupingBy(Track::artistId, Collectors.counting()));
        Set<Long> blockedArtists = perArtist.entrySet().stream()
                .filter(entry -> entry.getValue() >= BLOCKING_DISLIKES)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        return track -> filters.accepts(track)
                && !TrackMatcher.containedIn(track, dislikedKeys)
                && (track.artistId() == null || !blockedArtists.contains(track.artistId()));
    }

    public static List<Track> interleave(List<Track> first, List<Track> second) {
        List<Track> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int firstIndex = 0;
        int secondIndex = 0;
        int step = 0;
        while (out.size() < LIMIT && (firstIndex < first.size() || secondIndex < second.size())) {
            boolean takeFirst = (step % 3 != 2 && firstIndex < first.size()) || secondIndex >= second.size();
            Track track = takeFirst ? first.get(firstIndex++) : second.get(secondIndex++);
            step++;
            if (seen.add(dedupeKey(track))) {
                out.add(track);
            }
        }
        return out;
    }

    private static String dedupeKey(Track track) {
        return TrackMatcher.keys(track).stream()
                .filter(key -> key.startsWith("n:"))
                .findFirst()
                .orElse("d:" + track.deezerId());
    }

    private List<Track> artistPool(Playlist playlist, List<Track> mine, List<Track> seeds) {
        Map<Long, Long> weight = seeds.stream()
                .filter(track -> track.artistId() != null && track.artistId() > 0)
                .collect(Collectors.groupingBy(Track::artistId, Collectors.counting()));
        for (CommonTrack common : TrackMatcher.common(playlist.getSources(), mine)) {
            Long artistId = common.track().artistId();
            if (artistId != null && artistId > 0) {
                weight.merge(artistId, COMMON_TRACK_WEIGHT * common.count(), Long::sum);
            }
        }

        List<Long> artists = new ArrayList<>(weight.keySet());
        Collections.shuffle(artists);
        artists.sort(Comparator.comparingLong((Long artistId) -> weight.get(artistId)).reversed());

        return Parallel.flatMap(artists.stream().limit(TOP_ARTISTS).toList(), artistId -> {
            List<Track> tracks = new ArrayList<>(artistCatalog.artistTop(artistId, 4));
            List<Long> related = new ArrayList<>(artistCatalog.relatedArtists(artistId, RELATED_ARTISTS_FETCHED));
            Collections.shuffle(related);
            related.stream().limit(RELATED_ARTISTS_USED)
                    .forEach(relatedId -> tracks.addAll(artistCatalog.artistTop(relatedId, 3)));
            return tracks;
        });
    }
}
