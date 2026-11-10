package com.mixtape.application.port;

import com.mixtape.domain.style.StyleCandidate;
import com.mixtape.domain.style.StyleIntent;

import java.util.List;

public interface StyleCandidateSource {
    String key();

    String displayName();

    boolean available();

    List<StyleCandidate> candidates(StyleIntent intent);
}
