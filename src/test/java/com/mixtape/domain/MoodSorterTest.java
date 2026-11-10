package com.mixtape.domain;

import com.mixtape.domain.error.BusinessRuleException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoodSorterTest {
    private static Track t(long id) {
        return Track.of(id, "T" + id, "A", id, null, null, null);
    }

    private static List<Long> ids(List<Track> l) { return l.stream().map(Track::deezerId).toList(); }

    private final List<Track> tracks = List.of(t(1), t(6), t(2), t(3), t(7), t(4), t(5));
    private final Map<Long, Double> bpm = Map.of(1L, 140.0, 2L, 80.0, 3L, 120.0, 4L, 100.0, 5L, 160.0);

    @Test
    void calmToEnergeticOrdersByAscendingBpmAndKeepsUnknownAtTheEndInOriginalOrder() {
        assertEquals(List.of(2L, 4L, 3L, 1L, 5L, 6L, 7L),
                ids(MoodSorter.sort(tracks, bpm, MoodSorter.Mode.CALM_TO_ENERGETIC)));
    }

    @Test
    void energeticToCalmReversesIt() {
        assertEquals(List.of(5L, 1L, 3L, 4L, 2L, 6L, 7L),
                ids(MoodSorter.sort(tracks, bpm, MoodSorter.Mode.ENERGETIC_TO_CALM)));
    }

    @Test
    void waveRisesToThePeakInTheMiddleAndRelaxesAtTheEnd() {
        assertEquals(List.of(2L, 3L, 5L, 1L, 4L, 6L, 7L), ids(MoodSorter.sort(tracks, bpm, MoodSorter.Mode.WAVE)));
    }

    @Test
    void equalBpmKeepsOriginalOrder() {
        List<Track> same = List.of(t(1), t(2), t(3));
        Map<Long, Double> eq = Map.of(1L, 100.0, 2L, 100.0, 3L, 100.0);
        assertEquals(List.of(1L, 2L, 3L), ids(MoodSorter.sort(same, eq, MoodSorter.Mode.CALM_TO_ENERGETIC)));
    }

    @Test
    void parsesModesAndRejectsUnknownOnes() {
        assertEquals(MoodSorter.Mode.CALM_TO_ENERGETIC, MoodSorter.Mode.parse("asc"));
        assertEquals(MoodSorter.Mode.ENERGETIC_TO_CALM, MoodSorter.Mode.parse("desc"));
        assertEquals(MoodSorter.Mode.WAVE, MoodSorter.Mode.parse("wave"));
        assertThrows(BusinessRuleException.class, () -> MoodSorter.Mode.parse("aleatorio"));
    }
}
