package com.mixtape.application.port;

import java.util.Optional;
import java.util.OptionalDouble;

public interface TempoEstimator {
    Optional<OptionalDouble> estimate(long trackId);
}
