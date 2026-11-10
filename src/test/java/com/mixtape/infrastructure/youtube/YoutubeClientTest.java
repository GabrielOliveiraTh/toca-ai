package com.mixtape.infrastructure.youtube;

import com.mixtape.domain.error.BusinessRuleException;
import com.mixtape.infrastructure.youtube.YoutubeClient.VideoTrack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YoutubeClientTest {
    @Test
    void extractsPlaylistIdFromLinks() {
        assertEquals("PLabc_123-x", YoutubeClient.playlistId("https://www.youtube.com/playlist?list=PLabc_123-x"));
        assertEquals("PLabc", YoutubeClient.playlistId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLabc&index=2"));
        assertEquals("PLabc", YoutubeClient.playlistId("https://music.youtube.com/playlist?list=PLabc&si=zzz"));
        assertThrows(BusinessRuleException.class, () -> YoutubeClient.playlistId("https://youtu.be/dQw4w9WgXcQ"));
    }

    @Test
    void detectsYoutubeUrls() {
        assertTrue(YoutubeClient.isYoutubeUrl("https://www.youtube.com/playlist?list=PL1"));
        assertTrue(YoutubeClient.isYoutubeUrl("https://youtu.be/abc?list=PL1"));
        assertFalse(YoutubeClient.isYoutubeUrl("https://www.deezer.com/playlist/1"));
    }

    @Test
    void parsesArtistAndTitleFromVideoTitle() {
        assertEquals(new VideoTrack("Daft Punk", "One More Time"),
                YoutubeClient.parse("Daft Punk - One More Time (Official Video)", "Daft Punk"));
        assertEquals(new VideoTrack("Charlie Brown Jr.", "Dias de Luta, Dias de Glória"),
                YoutubeClient.parse("Charlie Brown Jr. - Dias de Luta, Dias de Glória [Clipe Oficial] HD", "ChorãoVEVO"));
        assertEquals(new VideoTrack("Justice", "Genesis"),
                YoutubeClient.parse("Justice - Genesis | Official Audio", "Justice"));
    }

    @Test
    void usesTopicChannelAsArtistWhenTitleHasNoArtist() {
        assertEquals(new VideoTrack("Skank", "Sutilmente"), YoutubeClient.parse("Sutilmente", "Skank - Topic"));
        assertEquals(new VideoTrack("Skank", "Sutilmente"), YoutubeClient.parse("Skank - Sutilmente", "Skank - Topic"));
    }

    @Test
    void fallsBackToChannelNameAndStripsFeaturing() {
        assertEquals(new VideoTrack("Legião Urbana", "Pais e Filhos"),
                YoutubeClient.parse("Pais e Filhos (Lyrics) ft. Alguém", "Legião Urbana"));
    }
}
