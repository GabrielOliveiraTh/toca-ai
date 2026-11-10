package com.mixtape.infrastructure.audio;

import com.mixtape.application.BpmService;
import com.mixtape.application.port.TrackMetadata;
import com.mixtape.support.FakeTrackMetadata;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BpmEstimatorTest {
    static final int RATE = 44_100;

    static short[] clicks(double bpm, double seconds) {
        short[] out = new short[(int) (RATE * seconds)];
        Random rnd = new Random(7);
        for (int i = 0; i < out.length; i++) {
            out[i] = (short) (rnd.nextGaussian() * 300);
        }
        double period = 60.0 / bpm * RATE;
        for (double t = 0; t < out.length; t += period) {
            for (int k = 0; k < 3000 && (int) t + k < out.length; k++) {
                double env = Math.exp(-k / 500.0);
                out[(int) t + k] += (short) (env * 12000 * Math.sin(2 * Math.PI * 80 * k / RATE) + env * rnd.nextGaussian() * 4000);
            }
        }
        return out;
    }

    @Test
    void findsTheTempoOfRegularBeats() {
        for (double bpm : new double[]{80, 100, 120, 128, 140, 160}) {
            OptionalDouble r = BpmEstimator.estimate(clicks(bpm, 30), RATE);
            assertTrue(r.isPresent(), "no result for " + bpm);
            assertEquals(bpm, r.getAsDouble(), 3.0, "expected " + bpm);
        }
    }

    @Test
    void steadyNoiseHasNoClearBeat() {
        short[] noise = new short[RATE * 30];
        Random rnd = new Random(1);
        for (int i = 0; i < noise.length; i++) {
            noise[i] = (short) (rnd.nextGaussian() * 3000);
        }
        assertTrue(BpmEstimator.estimate(noise, RATE).isEmpty());
    }

    @Test
    void silenceAndTooShortAudioGiveNothing() {
        assertTrue(BpmEstimator.estimate(new short[RATE * 30], RATE).isEmpty());
        assertTrue(BpmEstimator.estimate(clicks(120, 1), RATE).isEmpty());
    }

    @Test
    void fallsBackToTheEstimateWhenDeezerHasNoBpmAndRemembersIt() {
        int[] estimates = {0};
        TrackMetadata metadata = new FakeTrackMetadata() {
            @Override
            public Optional<OptionalDouble> bpm(long id) {
                return Optional.of(id == 1 ? OptionalDouble.of(100) : OptionalDouble.empty());
            }
        };
        BpmService svc = new BpmService(metadata, id -> {
            estimates[0]++;
            return Optional.of(OptionalDouble.of(123.4));
        });
        assertEquals(Optional.of(100.0), svc.bpm(1));
        assertFalse(svc.isEstimated(1));
        assertEquals(Optional.of(123.4), svc.bpm(2));
        assertEquals(Optional.of(123.4), svc.bpm(2));
        assertTrue(svc.isEstimated(2));
        assertEquals(1, estimates[0]);
    }

    @Test
    void failedAudioDownloadIsNotCached() {
        int[] tries = {0};
        TrackMetadata metadata = new FakeTrackMetadata() {
            @Override public Optional<OptionalDouble> bpm(long id) { return Optional.of(OptionalDouble.empty()); }
        };
        BpmService svc = new BpmService(metadata, id -> { tries[0]++; return Optional.empty(); });
        assertTrue(svc.bpm(5).isEmpty());
        assertTrue(svc.bpm(5).isEmpty());
        assertEquals(2, tries[0]);
    }
}
