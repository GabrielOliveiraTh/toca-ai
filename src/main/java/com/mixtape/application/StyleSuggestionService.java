package com.mixtape.application;

import com.mixtape.application.port.StyleCandidateSource;
import com.mixtape.application.port.TrackCatalog;
import com.mixtape.application.port.TrackMetadata;
import com.mixtape.domain.Track;
import com.mixtape.domain.style.CandidateGroup;
import com.mixtape.domain.style.StyleCandidate;
import com.mixtape.domain.style.StyleIntent;
import com.mixtape.domain.style.StyleScorer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class StyleSuggestionService {
    private static final Logger log = LoggerFactory.getLogger(StyleSuggestionService.class);

    private static final long TTL_MS = 20 * 60_000L;
    private static final int MAX_CACHED_STYLES = 40;
    private static final int MIN_CONFIRMED = 3;
    private static final int UNKNOWN_FILL = 12;
    private static final int RANKED_LIMIT = 150;
    private static final int MATCH_LIMIT = 60;
    private static final int YEAR_LOOKUP_LIMIT = 80;
    private static final int PICK_LIMIT = 40;
    private static final double YEAR_CONFIRMED_BONUS = 2.0;
    private static final double PICK_RANDOMNESS = 2.0;

    private record Scored(Track track, double score) {
    }

    private record Pool(long at, List<Scored> tracks) {
    }

    private final TrackCatalog catalog;
    private final TrackMetadata metadata;
    private final List<StyleCandidateSource> sources;
    private final StyleWorkers workers;
    private final DiscographyExpander discographies;
    private final Map<String, Pool> cache = new ConcurrentHashMap<>();

    public StyleSuggestionService(TrackCatalog catalog, TrackMetadata metadata, List<StyleCandidateSource> sources,
                                  StyleWorkers workers, DiscographyExpander discographies) {
        this.catalog = catalog;
        this.metadata = metadata;
        this.sources = sources;
        this.workers = workers;
        this.discographies = discographies;
    }

    public List<String> activeSources() {
        return available().stream().map(StyleCandidateSource::displayName).toList();
    }

    private List<StyleCandidateSource> available() {
        return sources.stream().filter(StyleCandidateSource::available).toList();
    }

    public List<Track> tracksFor(String style, Predicate<Track> accept) {
        if (style == null || style.isBlank()) {
            return List.of();
        }
        StyleIntent intent = StyleIntent.parse(style);
        List<StyleCandidateSource> active = available();
        String key = StyleIntent.strip(style) + "|"
                + active.stream().map(StyleCandidateSource::key).collect(Collectors.joining(","));
        Pool pool = cache.get(key);
        if (pool == null || System.currentTimeMillis() - pool.at() > TTL_MS) {
            List<Scored> built = build(intent, active);
            pool = new Pool(System.currentTimeMillis(), built);
            if (!built.isEmpty()) {
                if (cache.size() > MAX_CACHED_STYLES) {
                    cache.clear();
                }
                cache.put(key, pool);
            }
        }
        if (pool.tracks().isEmpty()) {
            return catalog.search(style).stream().filter(accept).toList();
        }
        return pick(pool.tracks(), accept);
    }

    private static List<Track> pick(List<Scored> tracks, Predicate<Track> accept) {
        Random random = new Random();
        record Ranked(Scored scored, double rank) {
        }
        return tracks.stream()
                .filter(scored -> accept.test(scored.track()))
                .map(scored -> new Ranked(scored, scored.score() + random.nextDouble() * PICK_RANDOMNESS))
                .sorted(Comparator.comparingDouble(Ranked::rank).reversed())
                .limit(PICK_LIMIT)
                .map(ranked -> ranked.scored().track())
                .toList();
    }

    private List<Scored> build(StyleIntent intent, List<StyleCandidateSource> active) {
        List<StyleCandidate> all = new ArrayList<>(workers.gather(active, intent));
        log.info("Style '{}': candidates per source {}", intent.raw(),
                all.stream().collect(Collectors.groupingBy(StyleCandidate::source, TreeMap::new,
                        Collectors.counting())));

        if (intent.hasYears()) {
            List<StyleCandidate> discography = discographies.expand(all, intent);
            log.info("Style '{}': {} tracks from discographies of the era", intent.raw(), discography.size());
            all.addAll(discography);
        }

        List<CandidateGroup> groups = StyleScorer.score(StyleScorer.group(all, intent), intent);
        List<CandidateGroup> ranked = groups.stream()
                .sorted(Comparator.comparingDouble((CandidateGroup group) -> group.score).reversed())
                .limit(RANKED_LIMIT)
                .toList();

        workers.runAll(ranked.stream()
                .filter(group -> group.catalogTrack == null)
                .limit(MATCH_LIMIT)
                .<Callable<Void>>map(group -> () -> {
                    catalog.match(group.isrc, group.title, group.artist)
                            .filter(found -> StyleScorer.plausible(group, found))
                            .ifPresent(found -> group.catalogTrack = found);
                    return null;
                })
                .toList());

        if (intent.hasYears()) {
            workers.runAll(ranked.stream()
                    .filter(group -> group.catalogTrack != null && group.catalogYear == null)
                    .limit(YEAR_LOOKUP_LIMIT)
                    .<Callable<Void>>map(group -> () -> {
                        metadata.releaseYear(group.catalogTrack.deezerId())
                                .ifPresent(year -> group.catalogYear = year);
                        return null;
                    })
                    .toList());
        }

        List<Scored> confirmed = new ArrayList<>();
        List<Scored> unknown = new ArrayList<>();
        for (CandidateGroup group : ranked) {
            if (group.catalogTrack == null) {
                continue;
            }
            double score = group.score;
            Track track = group.spotifyUri != null
                    ? group.catalogTrack.withSpotifyUri(group.spotifyUri)
                    : group.catalogTrack;
            if (!intent.hasYears()) {
                confirmed.add(new Scored(track, score));
                continue;
            }
            Integer year = group.catalogYear;
            if (year == null) {
                unknown.add(new Scored(track, score));
                continue;
            }
            if (!StyleScorer.inRange(year, intent)) {
                continue;
            }
            if (!group.yearScored) {
                score += YEAR_CONFIRMED_BONUS;
            }
            confirmed.add(new Scored(track, score));
        }
        log.info("Style '{}': {} confirmed, {} without a confirmed year", intent.raw(), confirmed.size(),
                unknown.size());
        if (intent.hasYears() && confirmed.size() < MIN_CONFIRMED) {
            unknown.stream()
                    .sorted(Comparator.comparingDouble(Scored::score).reversed())
                    .limit(UNKNOWN_FILL)
                    .forEach(confirmed::add);
        }
        return confirmed;
    }
}
