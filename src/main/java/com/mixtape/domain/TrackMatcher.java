package com.mixtape.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TrackMatcher {
    private static final int MIN_COMMON_SOURCES = 2;

    private TrackMatcher() {
    }

    public static Set<String> keys(Track track) {
        Set<String> keys = new HashSet<>();
        if (track.isrc() != null && !track.isrc().isBlank()) {
            keys.add("i:" + track.isrc().toUpperCase(Locale.ROOT));
        }
        if (track.deezerId() != null) {
            keys.add("d:" + track.deezerId());
        }
        String name = norm(track.artist()) + "|" + norm(track.title());
        if (name.length() > 1) {
            keys.add("n:" + name);
        }
        return keys;
    }

    public static boolean same(Track first, Track second) {
        return !Collections.disjoint(keys(first), keys(second));
    }

    public static Set<String> keysOf(Collection<Track> tracks) {
        Set<String> all = new HashSet<>();
        tracks.forEach(track -> all.addAll(keys(track)));
        return all;
    }

    public static boolean containedIn(Track track, Set<String> keySet) {
        return !Collections.disjoint(keys(track), keySet);
    }

    public static String norm(String text) {
        if (text == null) {
            return "";
        }
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        plain = plain.replaceAll("\\(.*?\\)|\\[.*?\\]", "").replaceAll("\\s-\\s.*$", "");
        return plain.replaceAll("[^a-z0-9]", "");
    }

    private static final class Group {
        final Track track;
        final Set<String> keys;
        final Map<Long, String> sources = new LinkedHashMap<>();

        Group(Track track) {
            this.track = track;
            this.keys = new HashSet<>(keys(track));
        }
    }

    public static List<CommonTrack> common(List<SourcePlaylist> sources, List<Track> mine) {
        List<Group> groups = new ArrayList<>();
        for (SourcePlaylist source : sources) {
            for (Track track : source.getTracks()) {
                Set<String> keys = keys(track);
                Group group = groups.stream()
                        .filter(existing -> !Collections.disjoint(existing.keys, keys))
                        .findFirst()
                        .orElse(null);
                if (group == null) {
                    group = new Group(track);
                    groups.add(group);
                }
                group.keys.addAll(keys);
                group.sources.put(source.getId(), source.getName());
            }
        }
        Set<String> mineKeys = keysOf(mine);
        return groups.stream()
                .filter(group -> group.sources.size() >= MIN_COMMON_SOURCES)
                .map(group -> new CommonTrack(group.track, group.sources.size(),
                        List.copyOf(group.sources.values()), !Collections.disjoint(group.keys, mineKeys)))
                .sorted(Comparator.comparingInt(CommonTrack::count).reversed()
                        .thenComparing(common -> common.track().artist(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
