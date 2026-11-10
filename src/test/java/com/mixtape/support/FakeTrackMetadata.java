package com.mixtape.support;

import com.mixtape.application.port.TrackMetadata;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public class FakeTrackMetadata implements TrackMetadata {
    @Override
    public Optional<OptionalDouble> bpm(long trackId) {
        return Optional.empty();
    }

    @Override
    public Optional<List<String>> genresOfTrack(long trackId) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> releaseYear(long trackId) {
        return Optional.empty();
    }
}
