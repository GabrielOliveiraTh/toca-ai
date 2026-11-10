package com.mixtape.domain.style;

import com.mixtape.application.SuggestionService;
import com.mixtape.domain.Track;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleIntentTest {
    @Test
    void understandsDecadeAndGenre() {
        StyleIntent i = StyleIntent.parse("Pagode anos 80");
        assertEquals(1980, i.yearFrom());
        assertEquals(1989, i.yearTo());
        assertEquals(List.of("pagode"), i.genres());
        assertEquals("anos 80", i.decadeLabel());
        assertTrue(i.playlistQueries().contains("pagode anos 80"));
    }

    @Test
    void understandsMoodOccasionAndTwoThousands() {
        StyleIntent i = StyleIntent.parse("Músicas animadas para aniversário dos anos 2000");
        assertEquals(2000, i.yearFrom());
        assertEquals(2009, i.yearTo());
        assertEquals("anos 2000", i.decadeLabel());
        assertTrue(i.terms().contains("animadas"));
        assertTrue(i.terms().contains("aniversário"));
        assertTrue(i.terms().contains("festa"));
        assertFalse(i.terms().contains("para"));
        assertFalse(i.terms().contains("2000"));
    }

    @Test
    void mapsSofrenciaToSertanejoWithoutPeriod() {
        StyleIntent i = StyleIntent.parse("Sertanejo sofrência");
        assertFalse(i.hasYears());
        assertEquals(List.of("sertanejo"), i.genres());
        assertTrue(i.terms().contains("sofrência"));
        assertNull(i.decadeLabel());
    }

    @Test
    void handlesSingleYearsAndShortDecades() {
        StyleIntent single = StyleIntent.parse("hits de 1994");
        assertEquals(1994, single.yearFrom());
        assertEquals(1994, single.yearTo());
        assertNull(single.decadeLabel());
        StyleIntent d00 = StyleIntent.parse("pop anos 00");
        assertEquals(2000, d00.yearFrom());
        assertEquals(2009, d00.yearTo());
        StyleIntent d70 = StyleIntent.parse("rock década de 70");
        assertEquals(1970, d70.yearFrom());
    }

    @Test
    void recognizesTitlesThatMentionTheRequestedPeriod() {
        StyleIntent i80 = StyleIntent.parse("Pagode anos 80");
        assertTrue(i80.matchesPeriod("Pagode Anos 80 - Os Melhores"));
        assertTrue(i80.matchesPeriod("Raízes do pagode 80s"));
        assertTrue(i80.matchesPeriod("Samba e Pagode 1987"));
        assertTrue(i80.matchesPeriod("Década de 80 Brasil"));
        assertFalse(i80.matchesPeriod("Pagode Anos 90"));
        assertFalse(i80.matchesPeriod("Pagode 2018"));
        assertFalse(i80.matchesPeriod("Top 180 pagodes"));
        StyleIntent i2000 = StyleIntent.parse("pop anos 2000");
        assertTrue(i2000.matchesPeriod("Hits Anos 2000"));
        assertTrue(i2000.matchesPeriod("2000s Pop Party"));
        assertTrue(i2000.matchesPeriod("Pop 2005"));
        assertFalse(i2000.matchesPeriod("Anos 2010 pop"));
        assertFalse(StyleIntent.parse("Sertanejo sofrência").matchesPeriod("Sertanejo anos 80"));
    }

    @Test
    void understandsEnglishDecadeAndGenre() {
        StyleIntent intent = StyleIntent.parse("80s rock");
        assertEquals(1980, intent.yearFrom());
        assertEquals(1989, intent.yearTo());
        assertEquals(List.of("rock"), intent.genres());
        assertEquals("80s", intent.decadeLabel());
        assertTrue(intent.playlistQueries().contains("rock 80s"));
    }

    @Test
    void understandsEnglishMoodOccasionAndTwoThousands() {
        StyleIntent intent = StyleIntent.parse("upbeat birthday songs from the 2000s");
        assertEquals(2000, intent.yearFrom());
        assertEquals(2009, intent.yearTo());
        assertEquals("2000s", intent.decadeLabel());
        assertTrue(intent.terms().contains("party"));
        assertFalse(intent.terms().contains("songs"));
        assertFalse(intent.terms().contains("2000s"));
    }

    @Test
    void understandsEnglishMoodWithGenre() {
        StyleIntent intent = StyleIntent.parse("sad country");
        assertEquals(List.of("country"), intent.genres());
        assertTrue(intent.terms().contains("sad"));
        assertFalse(intent.hasYears());
    }

    @Test
    void understandsSingleYearWithEnglishGenre() {
        StyleIntent intent = StyleIntent.parse("1994 grunge");
        assertEquals(1994, intent.yearFrom());
        assertEquals(1994, intent.yearTo());
        assertEquals(List.of("grunge"), intent.genres());
        assertNull(intent.decadeLabel());
    }

    @Test
    void emptyTextIsHarmless() {
        StyleIntent i = StyleIntent.parse("   ");
        assertFalse(i.hasYears());
        assertTrue(i.genres().isEmpty());
        assertTrue(i.playlistQueries().isEmpty());
    }

    @Test
    void interleaveFollowsTwoStyleOneArtistPatternWithoutDuplicates() {
        List<Track> style = List.of(t(1, "S1"), t(2, "S2"), t(3, "S3"), t(4, "S4"));
        List<Track> artists = List.of(t(5, "A1"), t(6, "A2"), t(2, "S2"));
        List<String> out = SuggestionService.interleave(style, artists).stream().map(Track::title).toList();
        assertEquals(List.of("S1", "S2", "A1", "S3", "S4", "A2"), out);
    }

    private static Track t(long id, String title) {
        return Track.of(id, title, "Artista " + title, id, null, null, null);
    }
}
