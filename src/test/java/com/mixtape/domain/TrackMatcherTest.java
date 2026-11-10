package com.mixtape.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackMatcherTest {
    private static Track t(long id, String title, String artist, String isrc) {
        return Track.of(id, title, artist, 1L, "Album", null, isrc);
    }

    private static SourcePlaylist source(long id, String name, Track... tracks) throws Exception {
        SourcePlaylist s = SourcePlaylist.of(null, name, null, null, false, List.of(tracks));
        Field f = SourcePlaylist.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(s, id);
        return s;
    }

    @Test
    void sameSongWithDifferentDeezerIdsMatchesByIsrcOrNormalizedName() {
        assertTrue(TrackMatcher.same(t(1, "One More Time", "Daft Punk", "ISRC1"), t(2, "Another title", "Outro", "isrc1")));
        assertTrue(TrackMatcher.same(t(1, "Génesis (Remastered)", "Justice", null), t(2, "Genesis - Radio Edit", "JUSTICE", null)));
        assertFalse(TrackMatcher.same(t(1, "Genesis", "Justice", null), t(2, "Genesis", "Outra Banda", null)));
    }

    @Test
    void commonKeepsOnlyTracksInTwoOrMoreSourcesAndFlagsMine() throws Exception {
        Track a = t(1, "Song A", "X", "A1");
        Track b = t(2, "Song B", "Y", "B1");
        Track c = t(3, "Song C", "Z", "C1");
        SourcePlaylist s1 = source(10, "P1", a, b);
        SourcePlaylist s2 = source(11, "P2", t(99, "Song A (Live)", "X", "A1"), c);
        SourcePlaylist s3 = source(12, "P3", a, c);

        List<CommonTrack> common = TrackMatcher.common(List.of(s1, s2, s3), List.of(b, c));

        assertEquals(2, common.size());
        assertEquals("Song A", common.get(0).track().title());
        assertEquals(3, common.get(0).count());
        assertFalse(common.get(0).inMine());
        assertEquals("Song C", common.get(1).track().title());
        assertEquals(2, common.get(1).count());
        assertTrue(common.get(1).inMine());
    }
}
