package com.mixtape.domain.style;

import com.mixtape.domain.Track;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleScorerTest {
    private static StyleCandidate candidate(String artist, String title, Integer year, String source) {
        return new StyleCandidate(artist, title, year, null, null, null, null, source, false);
    }

    private static CandidateGroup single(List<StyleCandidate> candidates, StyleIntent intent) {
        List<CandidateGroup> scored = StyleScorer.score(StyleScorer.group(candidates, intent), intent);
        assertEquals(1, scored.size());
        return scored.get(0);
    }

    @Test
    void twoSourcesWeighMoreThanOne() {
        StyleIntent intent = StyleIntent.parse("pagode");
        CandidateGroup one = single(List.of(candidate("Zeca", "Shrimp", null, "deezer")), intent);
        CandidateGroup two = single(List.of(
                candidate("Zeca", "Shrimp", null, "deezer"),
                candidate("ZECA", "Shrimp", null, "itunes")), intent);
        assertEquals(2, two.sources.size());
        assertTrue(two.score > one.score);
    }

    @Test
    void aYearOutsideThePeriodEliminatesTheSong() {
        StyleIntent intent = StyleIntent.parse("pagode anos 80");
        List<CandidateGroup> kept = StyleScorer.score(StyleScorer.group(List.of(
                candidate("A", "In range", 1985, "itunes"),
                candidate("B", "Too late", 1999, "itunes")), intent), intent);
        assertEquals(1, kept.size());
        assertEquals("In range", kept.get(0).title);
        assertTrue(kept.get(0).yearScored);
    }

    @Test
    void anUnknownYearIsKeptButScoresLower() {
        StyleIntent intent = StyleIntent.parse("pagode anos 80");
        CandidateGroup known = single(List.of(candidate("A", "Known", 1985, "itunes")), intent);
        CandidateGroup unknown = single(List.of(candidate("A", "Unknown", null, "itunes")), intent);
        assertTrue(known.score > unknown.score);
        assertFalse(unknown.yearScored);
    }

    @Test
    void plausibleRejectsADifferentArtistButAcceptsAMatchWithExtraText() {
        CandidateGroup group = new CandidateGroup();
        group.artist = "Zeca Pagodinho";
        group.title = "Deixa a vida me levar";
        Track same = Track.of(1L, "Deixa a Vida Me Levar (Ao Vivo)", "Zeca Pagodinho", 1L, null, null, null);
        Track otherArtist = Track.of(2L, "Deixa a vida me levar", "Outra Banda", 2L, null, null, null);
        assertTrue(StyleScorer.plausible(group, same));
        assertFalse(StyleScorer.plausible(group, otherArtist));
    }
}
