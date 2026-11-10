package com.mixtape.application.port;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public interface TrackMetadata {
    Optional<OptionalDouble> bpm(long trackId);

    Optional<List<String>> genresOfTrack(long trackId);

    Optional<Integer> releaseYear(long trackId);
}
