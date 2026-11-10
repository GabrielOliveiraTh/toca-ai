package com.mixtape.application;

import com.mixtape.application.port.TrackMetadata;
import com.mixtape.application.support.Parallel;
import com.mixtape.domain.Track;
import com.mixtape.domain.style.StyleIntent;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GenreFilter {
    static final int CHECK_LIMIT = 40;

    static final Map<String, List<String>> GENRES = new LinkedHashMap<>();

    static {
        GENRES.put("sertanejo", List.of("sertanejo", "country"));
        GENRES.put("funk", List.of("funk"));
        GENRES.put("pagode", List.of("pagode", "samba"));
        GENRES.put("forro", List.of("forro", "axe", "piseiro"));
        GENRES.put("mpb", List.of("mpb", "bossa", "brasileira", "brazilian"));
        GENRES.put("pop", List.of("pop"));
        GENRES.put("rock", List.of("rock", "metal", "punk", "alternativ", "indie"));
        GENRES.put("rap", List.of("rap", "hip hop", "hip-hop", "trap"));
        GENRES.put("electronic", List.of("electro", "eletron", "dance", "house", "techno"));
        GENRES.put("reggae", List.of("reggae"));
        GENRES.put("gospel", List.of("gospel", "religi", "christian"));
        GENRES.put("rnb", List.of("r&b", "soul"));
        GENRES.put("jazz", List.of("jazz", "blues"));
        GENRES.put("classical", List.of("classic", "clássic"));
        GENRES.put("latin", List.of("latin", "reggaeton", "salsa", "bachata"));
    }

    private final TrackMetadata metadata;
    // Only conclusive lookups are cached; a failed one lets the track pass and is retried later.
    private final Map<Long, List<String>> cache = new ConcurrentHashMap<>();

    public GenreFilter(TrackMetadata metadata) {
        this.metadata = metadata;
    }

    public static Set<String> parse(String csv) {
        Set<String> keys = new LinkedHashSet<>();
        if (csv == null) {
            return keys;
        }
        for (String item : csv.split(",")) {
            String key = StyleIntent.strip(item.trim());
            if (GENRES.containsKey(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    static boolean matches(Collection<String> genreNames, Set<String> excluded) {
        for (String name : genreNames) {
            String plain = StyleIntent.strip(name);
            for (String key : excluded) {
                for (String fragment : GENRES.getOrDefault(key, List.of())) {
                    if (plain.contains(StyleIntent.strip(fragment))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public List<Track> apply(List<Track> tracks, Set<String> excluded) {
        if (excluded.isEmpty() || tracks.isEmpty()) {
            return tracks;
        }
        List<Track> head = tracks.stream().limit(CHECK_LIMIT).toList();
        List<List<String>> genres = Parallel.map(head, this::genresOf);
        List<Track> kept = new ArrayList<>();
        for (int index = 0; index < head.size(); index++) {
            List<String> trackGenres = genres.get(index);
            if (trackGenres == null || !matches(trackGenres, excluded)) {
                kept.add(head.get(index));
            }
        }
        return kept;
    }

    private List<String> genresOf(Track track) {
        List<String> cached = cache.get(track.deezerId());
        if (cached != null) {
            return cached;
        }
        Optional<List<String>> lookup = metadata.genresOfTrack(track.deezerId());
        if (lookup.isEmpty()) {
            return null;
        }
        cache.putIfAbsent(track.deezerId(), lookup.get());
        return lookup.get();
    }
}
