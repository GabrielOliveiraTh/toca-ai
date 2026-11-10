package com.mixtape.domain.style;

import com.mixtape.domain.Track;

public record StyleCandidate(String artist, String title, Integer year, String genre, String isrc, String spotifyUri,
                             Track catalogTrack, String source, boolean eraHint) {
}
