package com.mixtape.domain;

import java.util.Set;
import java.util.regex.Pattern;

public record SuggestionFilters(boolean avoidLive, boolean avoidRemix, boolean popularOnly,
                                Set<String> excludedGenres) {
    static final int POPULAR_RANK = 300_000;

    private static final Pattern LIVE_PAREN = Pattern.compile(
            "(?i)[(\\[\\-–]\\s*[^)\\]]*\\b(ao vivo|live|acústico|acustico|unplugged)\\b");
    private static final Pattern LIVE_END = Pattern.compile("(?i)\\bao vivo\\s*$");
    private static final Pattern LIVE_ALBUM = Pattern.compile(
            "(?i)\\b(ao vivo|live at|live in|live from|unplugged|acústico|acustico)\\b");

    private static final Pattern VERSION_PAREN = Pattern.compile(
            "(?i)[(\\[\\-–]\\s*[^)\\]]*\\b(remix|mix|radio edit|edit|extended|version|versão|versao|cover|tribute"
                    + "|tributo|sped up|slowed|acapella|a cappella|mashup|bootleg|rework|karaoke|karaokê"
                    + "|instrumental)\\b");
    private static final Pattern VERSION_ANYWHERE = Pattern.compile("(?i)\\b(remix|karaoke|karaokê|instrumental)\\b");

    public SuggestionFilters {
        excludedGenres = excludedGenres == null ? Set.of() : Set.copyOf(excludedGenres);
    }

    public SuggestionFilters(boolean avoidLive, boolean avoidRemix, boolean popularOnly) {
        this(avoidLive, avoidRemix, popularOnly, Set.of());
    }

    public static SuggestionFilters none() {
        return new SuggestionFilters(false, false, false, Set.of());
    }

    public boolean isNone() {
        return !avoidLive && !avoidRemix && !popularOnly && excludedGenres.isEmpty();
    }

    public boolean accepts(Track track) {
        String title = track.title() == null ? "" : track.title();
        String album = track.album() == null ? "" : track.album();
        if (avoidLive && isLive(title, album)) {
            return false;
        }
        if (avoidRemix && isAlternateVersion(title)) {
            return false;
        }
        return !(popularOnly && track.rank() != null && track.rank() < POPULAR_RANK);
    }

    private static boolean isLive(String title, String album) {
        return LIVE_PAREN.matcher(title).find()
                || LIVE_END.matcher(title).find()
                || LIVE_ALBUM.matcher(album).find();
    }

    private static boolean isAlternateVersion(String title) {
        return VERSION_PAREN.matcher(title).find() || VERSION_ANYWHERE.matcher(title).find();
    }
}
