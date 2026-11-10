package com.mixtape.domain.style;

import com.mixtape.domain.Track;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CandidateGroup {
    public final Set<String> keys = new HashSet<>();
    public final Set<String> sources = new LinkedHashSet<>();
    public Track catalogTrack;
    public String artist;
    public String title;
    public String isrc;
    public String spotifyUri;
    public Integer year;
    public Integer catalogYear;
    public boolean genreMatch;
    public boolean yearScored;
    public double score;
}
