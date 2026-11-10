package com.mixtape.domain;

import com.mixtape.domain.error.BusinessRuleException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MoodSorter {
    private MoodSorter() {
    }

    public enum Mode {
        CALM_TO_ENERGETIC,
        ENERGETIC_TO_CALM,
        WAVE;

        public static Mode parse(String text) {
            String mode = text == null ? "" : text.toLowerCase(Locale.ROOT);
            return switch (mode) {
                case "asc", "calm-to-energetic" -> CALM_TO_ENERGETIC;
                case "desc", "energetic-to-calm" -> ENERGETIC_TO_CALM;
                case "wave", "arc" -> WAVE;
                default -> throw new BusinessRuleException("Unknown sort mode: " + text);
            };
        }
    }

    public static List<Track> sort(List<Track> tracks, Map<Long, Double> bpmById, Mode mode) {
        List<Track> known = new ArrayList<>();
        List<Track> unknown = new ArrayList<>();
        for (Track track : tracks) {
            (bpmById.containsKey(track.deezerId()) ? known : unknown).add(track);
        }
        known.sort(Comparator.comparingDouble(track -> bpmById.get(track.deezerId())));

        List<Track> ordered = switch (mode) {
            case CALM_TO_ENERGETIC -> known;
            case ENERGETIC_TO_CALM -> reversed(known);
            case WAVE -> wave(known);
        };
        List<Track> result = new ArrayList<>(ordered);
        result.addAll(unknown);
        return result;
    }

    private static List<Track> reversed(List<Track> tracks) {
        List<Track> copy = new ArrayList<>(tracks);
        Collections.reverse(copy);
        return copy;
    }

    private static List<Track> wave(List<Track> sortedByTempo) {
        List<Track> rising = new ArrayList<>();
        List<Track> falling = new ArrayList<>();
        for (int index = 0; index < sortedByTempo.size(); index++) {
            (index % 2 == 0 ? rising : falling).add(sortedByTempo.get(index));
        }
        Collections.reverse(falling);
        List<Track> ordered = new ArrayList<>(rising);
        ordered.addAll(falling);
        return ordered;
    }
}
