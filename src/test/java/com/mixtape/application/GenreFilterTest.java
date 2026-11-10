package com.mixtape.application;

import com.mixtape.application.port.TrackMetadata;
import com.mixtape.domain.Track;
import com.mixtape.support.FakeTrackMetadata;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenreFilterTest {
    @Test
    void parseKeepsOnlyKnownKeys() {
        assertEquals(Set.of("funk", "sertanejo"), GenreFilter.parse(" Funk, sertanejo ,xyz,"));
        assertTrue(GenreFilter.parse(null).isEmpty());
    }

    @Test
    void matchesDeezerGenreNamesByFragment() {
        assertTrue(GenreFilter.matches(List.of("Samba/Pagode"), Set.of("pagode")));
        assertTrue(GenreFilter.matches(List.of("Rap/Hip Hop", "Pop"), Set.of("rap")));
        assertTrue(GenreFilter.matches(List.of("Música Eletrônica"), Set.of("electronic")));
        assertFalse(GenreFilter.matches(List.of("Pop"), Set.of("rock", "funk")));
        assertFalse(GenreFilter.matches(List.of(), Set.of("pop")));
    }

    @Test
    void dropsExcludedTracksKeepsUnknownAndFailedLookups() {
        TrackMetadata metadata = new FakeTrackMetadata() {
            @Override public Optional<List<String>> genresOfTrack(long id) {
                if (id == 1) {
                    return Optional.of(List.of("Pop"));
                }
                if (id == 2) {
                    return Optional.of(List.of("Rock"));
                }
                if (id == 3) {
                    return Optional.of(List.of());
                }
                return Optional.empty();
            }
        };
        GenreFilter f = new GenreFilter(metadata);
        List<Track> in = List.of(t(1), t(2), t(3), t(4));
        assertEquals(List.of(2L, 3L, 4L), f.apply(in, Set.of("pop")).stream().map(Track::deezerId).toList());
        assertSame(in, f.apply(in, Set.of()));
    }

    private static Track t(long id) { return Track.of(id, "t" + id, "a", 9L, "al", null, null); }
}
