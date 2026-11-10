package com.mixtape.infrastructure.audio;

import java.util.OptionalDouble;

final class BpmEstimator {
    private static final int HOP = 512;
    private static final int MIN_BLOCKS = 200;
    private static final double MIN_BPM = 60;
    private static final double MAX_BPM = 200;
    private static final double MIN_CONFIDENCE = 0.12;
    private static final double PREFERRED_BPM = 110.0;
    private static final double PREFERENCE_WIDTH = 0.8;

    private BpmEstimator() {
    }

    static OptionalDouble estimate(short[] mono, int sampleRate) {
        int blocks = mono.length / HOP;
        if (blocks < MIN_BLOCKS || sampleRate <= 0) {
            return OptionalDouble.empty();
        }
        double frameRate = (double) sampleRate / HOP;

        double[] onset = onsetCurve(mono, blocks);
        double energy = 0;
        for (double value : onset) {
            energy += value * value;
        }
        if (energy < 1e-9) {
            return OptionalDouble.empty();
        }

        int minLag = (int) Math.floor(60.0 * frameRate / MAX_BPM);
        int maxLag = (int) Math.ceil(60.0 * frameRate / MIN_BPM);
        double[] autocorrelation = autocorrelation(onset, blocks, energy, minLag, maxLag);

        double bestScore = -1;
        double bestBpm = 0;
        double bestRaw = 0;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double raw = autocorrelation[lag];
            boolean isPeak = raw > 0 && raw >= autocorrelation[lag - 1] && raw >= autocorrelation[lag + 1];
            if (!isPeak) {
                continue;
            }
            double score = raw + 0.5 * at(autocorrelation, lag * 2) + 0.25 * at(autocorrelation, lag * 4);
            double bpm = 60.0 * frameRate / refine(autocorrelation, lag);
            double octaves = Math.log(bpm / PREFERRED_BPM) / Math.log(2);
            score *= Math.exp(-octaves * octaves / (2 * PREFERENCE_WIDTH * PREFERENCE_WIDTH));
            if (score > bestScore) {
                bestScore = score;
                bestBpm = bpm;
                bestRaw = raw;
            }
        }
        if (bestRaw < MIN_CONFIDENCE) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Math.round(bestBpm * 10) / 10.0);
    }

    private static double[] onsetCurve(short[] mono, int blocks) {
        double[] logEnergy = new double[blocks];
        for (int block = 0; block < blocks; block++) {
            double energy = 0;
            for (int i = block * HOP; i < (block + 1) * HOP; i++) {
                energy += (double) mono[i] * mono[i];
            }
            logEnergy[block] = Math.log(1e-3 + energy / HOP);
        }
        double[] onset = new double[blocks];
        double mean = 0;
        for (int block = 1; block < blocks; block++) {
            onset[block] = Math.max(0, logEnergy[block] - logEnergy[block - 1]);
            mean += onset[block];
        }
        mean /= blocks;
        for (int block = 0; block < blocks; block++) {
            onset[block] -= mean;
        }
        return onset;
    }

    private static double[] autocorrelation(double[] onset, int blocks, double energy, int minLag, int maxLag) {
        double[] result = new double[maxLag * 4 + 2];
        for (int lag = minLag / 2; lag < result.length && lag < blocks / 2; lag++) {
            double sum = 0;
            for (int i = lag; i < blocks; i++) {
                sum += onset[i] * onset[i - lag];
            }
            result[lag] = sum / energy * ((double) blocks / (blocks - lag));
        }
        return result;
    }

    private static double at(double[] values, int lag) {
        return lag < values.length ? Math.max(0, values[lag]) : 0;
    }

    private static double refine(double[] values, int lag) {
        double before = values[lag - 1];
        double peak = values[lag];
        double after = values[lag + 1];
        double curvature = before - 2 * peak + after;
        return curvature == 0 ? lag : lag + 0.5 * (before - after) / curvature;
    }
}
