package com.mixtape.application;

import com.mixtape.application.view.DiffView;
import com.mixtape.application.view.PlaylistView;
import com.mixtape.domain.CommonTrack;
import com.mixtape.domain.Playlist;
import com.mixtape.domain.PlaylistRepository;
import com.mixtape.domain.SourcePlaylist;
import com.mixtape.domain.Track;
import com.mixtape.domain.error.BusinessRuleException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "server.port=0"})
class PlaylistServiceTest {
    @Autowired PlaylistService service;
    @Autowired SourceService sources;
    @Autowired ComparisonService comparison;
    @Autowired DislikeService dislikeService;
    @Autowired PlaylistRepository repo;
    @Autowired TransactionTemplate tx;

    private static Track t(long id, String title, String artist, String isrc) {
        return Track.of(id, title, artist, 7L, "Album", "http://c/" + id, isrc).withSpotifyUri("spotify:track:" + id);
    }

    private static SourcePlaylist source(Playlist p, String name, boolean main, Track... tracks) {
        return SourcePlaylist.of(p, name, "https://open.spotify.com/playlist/" + name, name, main, List.of(tracks));
    }

    @Test
    void sourcesPersistAndDiffAndCommonWork() {
        Track a = t(1, "Song A", "X", "A1"), b = t(2, "Song B", "Y", "B1"),
                c = t(3, "Song C", "Z", "C1"), d = t(4, "Song D", "W", "D1");

        Long id = tx.execute(s -> {
            Playlist p = new Playlist();
            p.setName("minha");
            p.getSources().add(source(p, "ref", true, a, b, c));
            p.getSources().add(source(p, "cmp1", false, b, d));
            p.getSources().add(source(p, "cmp2", false, c, b));
            return repo.save(p).getId();
        });

        service.addTrack(id, a);
        service.addTrack(id, t(50, "Song B (Live)", "Y", "B1"));
        PlaylistView view = service.addTrack(id, t(50, "Song B (Live)", "Y", "B1"));
        assertEquals(2, view.tracks().size());
        assertEquals(3, view.sources().size());
        assertTrue(view.sources().get(0).main());
        assertEquals("http://c/1", view.sources().get(0).tracks().get(0).cover());

        DiffView diff = comparison.diff(id, null);
        assertEquals("ref", diff.sourceName());
        assertEquals(2, diff.both().size());
        assertTrue(diff.onlyMine().isEmpty());
        assertEquals(List.of("Song C"), diff.onlySource().stream().map(Track::title).toList());

        List<CommonTrack> common = comparison.common(id);
        assertEquals("Song B", common.get(0).track().title());
        assertEquals(3, common.get(0).count());
        assertTrue(common.get(0).inMine());

        PlaylistView afterRemove = sources.removeSource(id, view.sources().get(1).id());
        assertEquals(2, afterRemove.sources().size());
    }

    @Test
    void replaceTracksRestoresAPreviousStateForUndo() {
        Long id = service.create("undo", null, null, null).id();
        Track a = t(1, "A", "X", "A1"), b = t(2, "B", "Y", "B1"), c = t(3, "C", "Z", "C1");
        service.addTrack(id, a);
        service.addTrack(id, b);
        List<Track> snapshot = service.get(id).tracks();
        service.addTrack(id, c);
        service.removeTrack(id, 1L);
        assertEquals(List.of("B", "C"), service.get(id).tracks().stream().map(Track::title).toList());

        PlaylistView restored = service.replaceTracks(id, snapshot);
        assertEquals(List.of("A", "B"), restored.tracks().stream().map(Track::title).toList());
        assertEquals(List.of("A", "B"), service.get(id).tracks().stream().map(Track::title).toList());
        service.delete(id);
    }

    @Test
    void dislikesArePersistedCountedAndRestorable() {
        Long id = service.create("dislikes", null, "Pagode anos 80", null).id();
        assertEquals("Pagode anos 80", service.get(id).style());
        assertEquals(1, dislikeService.addDislike(id, t(9, "Ruim", "X", "R1")).dislikeCount());
        assertEquals(1, dislikeService.addDislike(id, t(9, "Ruim", "X", "R1")).dislikeCount());
        assertEquals(List.of("Ruim"), dislikeService.dislikes(id).stream().map(Track::title).toList());
        assertEquals(0, dislikeService.removeDislike(id, 9L).dislikeCount());
        service.delete(id);
    }

    @Test
    void updateChangesStyleWithoutTouchingTheNameAndEmptyStyleClearsIt() {
        Long id = service.create("nome", null, null, null).id();
        PlaylistView v = service.update(id, null, "  Country   road trip ", null);
        assertEquals("nome", v.name());
        assertEquals("Country road trip", v.style());
        assertNull(service.update(id, null, "   ", null).style());
        service.delete(id);
    }

    @Test
    void diffWithoutReferenceFailsClearly() {
        Long id = service.create("vazia", null, null, null).id();
        assertThrows(BusinessRuleException.class, () -> comparison.diff(id, null));
    }
}
