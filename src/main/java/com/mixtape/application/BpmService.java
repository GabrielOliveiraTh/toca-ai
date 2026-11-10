package com.mixtape.application;

import com.mixtape.application.port.TempoEstimator;
import com.mixtape.application.port.TrackMetadata;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BpmService {
    private final TrackMetadata metadata;
    private final TempoEstimator estimator;
    // Only conclusive answers are cached; a network failure is not.
    private final Map<Long, Optional<Double>> cache = new ConcurrentHashMap<>();
    private final Set<Long> estimated = ConcurrentHashMap.newKeySet();

    public BpmService(TrackMetadata metadata, TempoEstimator estimator) {
        this.metadata = metadata;
        this.estimator = estimator;
    }

    public Optional<Double> bpm(long deezerId) {
        Optional<Double> cached = cache.get(deezerId);
        if (cached != null) {
            return cached;
        }
        // No network call inside computeIfAbsent: it holds the map bin during I/O and can deadlock.
        Optional<OptionalDouble> lookup = metadata.bpm(deezerId);
        if (lookup.isEmpty()) {
            return Optional.empty();
        }
        boolean fromAudio = false;
        if (lookup.get().isEmpty()) {
            lookup = estimator.estimate(deezerId);
            if (lookup.isEmpty()) {
                return Optional.empty();
            }
            fromAudio = true;
        }
        Optional<Double> result = lookup.get().isPresent()
                ? Optional.of(lookup.get().getAsDouble())
                : Optional.empty();
        cache.putIfAbsent(deezerId, result);
        if (fromAudio && result.isPresent()) {
            estimated.add(deezerId);
        }
        return result;
    }

    public boolean isEstimated(long deezerId) {
        return estimated.contains(deezerId);
    }
}
