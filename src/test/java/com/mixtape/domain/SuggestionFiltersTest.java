package com.mixtape.domain;

import com.mixtape.application.SuggestionService;

import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionFiltersTest {
    private static Track t(String title, String album, Integer rank) {
        return Track.of(1L, title, "Artista", 9L, album, null, null).withRank(rank);
    }

    @Test
    void avoidLiveDropsLiveVersionsButKeepsTitlesThatMerelyContainTheWord() {
        SuggestionFilters f = new SuggestionFilters(true, false, false);
        assertFalse(f.accepts(t("Infiel (Ao Vivo)", "X", null)));
        assertFalse(f.accepts(t("Seu Astral (Live In Sao Paulo / 2010)", "X", null)));
        assertFalse(f.accepts(t("Estranho (Ao Vivo | Acústico)", "X", null)));
        assertFalse(f.accepts(t("Na Hora de Amar Ao Vivo", "X", null)));
        assertFalse(f.accepts(t("Paredes", "Jorge & Mateus Ao Vivo em Goiânia", null)));
        assertTrue(f.accepts(t("Live and Let Die", "Living Thing", null)));
        assertTrue(f.accepts(t("Alive", "Ten", null)));
        assertTrue(f.accepts(t("Infiel", "Album de estudio", null)));
    }

    @Test
    void avoidRemixDropsAlternativeVersions() {
        SuggestionFilters f = new SuggestionFilters(false, true, false);
        assertFalse(f.accepts(t("Adoleta (Remix TC)", "X", null)));
        assertFalse(f.accepts(t("Get Lucky - Radio Edit", "X", null)));
        assertFalse(f.accepts(t("Pais e Filhos (Karaoke Version)", "X", null)));
        assertFalse(f.accepts(t("Wannabe (Instrumental)", "X", null)));
        assertFalse(f.accepts(t("Baba (Cover)", "X", null)));
        assertTrue(f.accepts(t("Cover Me", "Born in the USA", null)));
        assertTrue(f.accepts(t("Remixed Feelings", "X", null)));
        assertTrue(f.accepts(t("Ainda Gosto Dela", "X", null)));
    }

    @Test
    void popularOnlyUsesDeezerRankAndLetsUnknownThrough() {
        SuggestionFilters f = new SuggestionFilters(false, false, true);
        assertTrue(f.accepts(t("Hit", "X", 700_000)));
        assertTrue(f.accepts(t("Classic", "X", 300_000)));
        assertFalse(f.accepts(t("Obscure", "X", 120_000)));
        assertTrue(f.accepts(t("No data", "X", null)));
    }

    @Test
    void noneAcceptsEverything() {
        assertTrue(SuggestionFilters.none().isNone());
        assertTrue(SuggestionFilters.none().accepts(t("Qualquer (Remix) (Ao Vivo)", "Ao Vivo", 1)));
    }

    @Test
    void dislikedTrackIsNeverSuggestedAndTwoDislikesBlockTheArtist() {
        Playlist p = new Playlist();
        p.getDislikes().add(dislike(p, 100L, 9L, "Artista", "Ruim 1"));
        Predicate<Track> one = SuggestionService.acceptance(p, SuggestionFilters.none());
        assertFalse(one.test(Track.of(100L, "Ruim 1", "Artista", 9L, null, null, null)));
        assertFalse(one.test(Track.of(555L, "Ruim 1", "Artista", 9L, null, null, null)));
        assertTrue(one.test(Track.of(101L, "Outra", "Artista", 9L, null, null, null)));

        p.getDislikes().add(dislike(p, 101L, 9L, "Artista", "Ruim 2"));
        Predicate<Track> two = SuggestionService.acceptance(p, SuggestionFilters.none());
        assertFalse(two.test(Track.of(102L, "Qualquer", "Artista", 9L, null, null, null)));
        assertTrue(two.test(Track.of(103L, "De outro", "Outro", 8L, null, null, null)));
    }

    @Test
    void acceptanceCombinesFiltersAndDislikes() {
        Playlist p = new Playlist();
        Predicate<Track> accept = SuggestionService.acceptance(p, new SuggestionFilters(true, false, false));
        assertFalse(accept.test(t("Track (Ao Vivo)", "X", null)));
        assertTrue(accept.test(t("Track", "X", null)));
    }

    private static Dislike dislike(Playlist p, long deezerId, long artistId, String artist, String title) {
        return Dislike.of(p, Track.of(deezerId, title, artist, artistId, null, null, null));
    }
}
