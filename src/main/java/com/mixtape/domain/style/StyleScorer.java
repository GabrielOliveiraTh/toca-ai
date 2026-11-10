package com.mixtape.domain.style;

import com.mixtape.domain.Track;
import com.mixtape.domain.TrackMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class StyleScorer {
    public static final String DISCOGRAPHY_SOURCE = "discography";
    public static final String SPOTIFY_SOURCE = "spotify";

    private StyleScorer() {
    }

    public static List<CandidateGroup> group(List<StyleCandidate> candidates, StyleIntent intent) {
        List<CandidateGroup> groups = new ArrayList<>();
        for (StyleCandidate candidate : candidates) {
            Track probe = candidate.catalogTrack() != null
                    ? candidate.catalogTrack()
                    : Track.probe(candidate.title(), candidate.artist(), candidate.isrc());
            Set<String> keys = TrackMatcher.keys(probe);
            CandidateGroup group = groups.stream()
                    .filter(existing -> !Collections.disjoint(existing.keys, keys))
                    .findFirst()
                    .orElse(null);
            if (group == null) {
                group = new CandidateGroup();
                groups.add(group);
            }
            merge(group, candidate, keys, intent);
        }
        return groups;
    }

    private static void merge(CandidateGroup group, StyleCandidate candidate, Set<String> keys, StyleIntent intent) {
        group.keys.addAll(keys);
        group.sources.add(candidate.source());
        if (candidate.catalogTrack() != null && group.catalogTrack == null) {
            group.catalogTrack = candidate.catalogTrack();
        }
        if (group.artist == null) {
            group.artist = candidate.artist();
            group.title = candidate.title();
        }
        if (candidate.isrc() != null && group.isrc == null) {
            group.isrc = candidate.isrc();
        }
        if (candidate.spotifyUri() != null) {
            group.spotifyUri = candidate.spotifyUri();
        }
        if (DISCOGRAPHY_SOURCE.equals(candidate.source()) && candidate.year() != null) {
            group.catalogYear = candidate.year();
        } else if (candidate.year() != null) {
            group.year = group.year == null ? candidate.year() : Math.min(group.year, candidate.year());
        }
        if (matchesGenre(candidate.genre(), intent)) {
            group.genreMatch = true;
        }
    }

    private static boolean matchesGenre(String genre, StyleIntent intent) {
        if (genre == null || genre.isBlank()) {
            return false;
        }
        String plain = StyleIntent.strip(genre);
        return intent.genres().stream().anyMatch(wanted -> plain.contains(StyleIntent.strip(wanted)));
    }

    public static List<CandidateGroup> score(List<CandidateGroup> groups, StyleIntent intent) {
        List<CandidateGroup> kept = new ArrayList<>();
        for (CandidateGroup group : groups) {
            int sourceCount = group.sources.size();
            double score = sourceCount + Math.max(0, sourceCount - 1) * 1.5;
            if (group.sources.contains(SPOTIFY_SOURCE)) {
                score += 0.3;
            }
            if (group.genreMatch) {
                score += 0.5;
            }
            if (intent.hasYears()) {
                Integer year = group.catalogYear != null ? group.catalogYear : group.year;
                if (year != null) {
                    if (!inRange(year, intent)) {
                        continue;
                    }
                    score += 1.5;
                    group.yearScored = true;
                } else {
                    score -= 0.5;
                }
            }
            group.score = score;
            kept.add(group);
        }
        return kept;
    }

    public static boolean inRange(int year, StyleIntent intent) {
        return year >= intent.yearFrom() && year <= intent.yearTo();
    }

    public static boolean plausible(CandidateGroup group, Track found) {
        String wantedArtist = TrackMatcher.norm(group.artist);
        String foundArtist = TrackMatcher.norm(found.artist());
        String wantedTitle = TrackMatcher.norm(group.title);
        String foundTitle = TrackMatcher.norm(found.title());
        boolean artistOk = wantedArtist.isEmpty() || foundArtist.contains(wantedArtist)
                || wantedArtist.contains(foundArtist);
        boolean titleOk = wantedTitle.isEmpty() || foundTitle.contains(wantedTitle)
                || wantedTitle.contains(foundTitle);
        return artistOk && titleOk;
    }
}
