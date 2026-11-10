package com.mixtape.infrastructure.deezer;

import com.mixtape.domain.error.BusinessRuleException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeezerClientTest {
    @Test
    void extractsPlaylistIdFromDeezerLinks() {
        assertEquals("1479458365", DeezerClient.playlistId("https://www.deezer.com/br/playlist/1479458365"));
        assertEquals("123", DeezerClient.playlistId("https://deezer.com/playlist/123?utm_source=x"));
        assertEquals("456", DeezerClient.playlistId("https://www.deezer.com/pt-br/playlist/456"));
    }

    @Test
    void rejectsLinksThatAreNotPlaylists() {
        assertThrows(BusinessRuleException.class, () -> DeezerClient.playlistId("https://www.deezer.com/br/album/99"));
        assertThrows(BusinessRuleException.class, () -> DeezerClient.playlistId("https://link.deezer.com/s/abc"));
    }

    @Test
    void detectsDeezerUrls() {
        assertTrue(DeezerClient.isDeezerUrl("https://www.deezer.com/br/playlist/1"));
        assertFalse(DeezerClient.isDeezerUrl("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"));
    }
}
