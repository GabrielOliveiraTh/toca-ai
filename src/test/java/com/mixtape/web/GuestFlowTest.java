package com.mixtape.web;

import com.mixtape.application.GuestService;
import com.mixtape.application.PlaylistService;
import com.mixtape.application.SharingService;
import com.mixtape.application.view.PlaylistView;
import com.mixtape.domain.Track;
import com.mixtape.domain.error.BusinessRuleException;
import com.mixtape.web.guest.GuestRateLimiter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:guestdb;DB_CLOSE_DELAY=-1", "server.port=0"})
@AutoConfigureMockMvc
class GuestFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired PlaylistService service;
    @Autowired SharingService sharing;
    @Autowired GuestService guests;

    static final String CDN = "https://cdn-images.dzcdn.net/images/cover/abc/250x250.jpg";

    private static RequestPostProcessor phone() {
        return r -> { r.setRemoteAddr("192.168.1.20"); r.addHeader("Host", "192.168.1.10:8080"); return r; };
    }

    private static Track t(long id, String title, String cover) {
        return Track.of(id, title, "Artist " + id, id, "Album", cover, "ISRC" + id)
                .withSpotifyUri("spotify:track:evil")
                .withRank(500_000);
    }

    private ResultActions info(String token, String guestId) throws Exception {
        var req = get("/api/guest/" + token + "/info").with(phone());
        if (guestId != null) {
            req = req.header("X-Guest-Id", guestId);
        }
        return mvc.perform(req);
    }

    private ResultActions add(String token, String guestId, Track track, String name) throws Exception {
        var req = post("/api/guest/" + token + "/tracks").with(phone()).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("track", track, "name", name)));
        if (guestId != null) {
            req = req.header("X-Guest-Id", guestId);
        }
        return mvc.perform(req);
    }

    private ResultActions remove(String token, String guestId, long deezerId) throws Exception {
        return mvc.perform(delete("/api/guest/" + token + "/tracks/" + deezerId)
                .with(phone())
                .header("X-Guest-Id", guestId));
    }

    private static String gid(String tag) { return (tag + "-0123456789abcdefghijklmnop").substring(0, 24); }

    @Test
    void fullGuestJourneyWithAuthorshipPermissionsAndRevocation() throws Exception {
        Long id = service.create("Festa", null, "Pagode anos 80", "Alex").id();
        service.addTrack(id, t(1, "From the owner", CDN));
        String token = sharing.enableShare(id);
        String ana = gid("ana"), bia = gid("bia");

        String json = info(token, ana).andExpect(status().isOk())
                .andExpect(jsonPath("$.playlistName").value("Festa"))
                .andExpect(jsonPath("$.ownerName").value("Alex"))
                .andExpect(jsonPath("$.tracks[0].addedBy").value("Alex"))
                .andExpect(jsonPath("$.tracks[0].byOwner").value(true))
                .andExpect(jsonPath("$.tracks[0].mine").value(false))
                .andReturn().getResponse().getContentAsString();
        assertFalse(json.contains("addedById"), "a guest must not receive authorship ids");
        assertFalse(json.contains("g:"));
        assertFalse(json.contains("spotify"), "nothing from the owner Spotify account reaches the guest");

        add(token, ana, t(2, "Da Ana", CDN), "Ana").andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks[1].addedBy").value("Ana"))
                .andExpect(jsonPath("$.tracks[1].mine").value(true))
                .andExpect(jsonPath("$.tracks[1].byOwner").value(false))
                .andExpect(jsonPath("$.tracks[1].addedAt").exists());
        info(token, bia)
                .andExpect(jsonPath("$.tracks[1].addedBy").value("Ana"))
                .andExpect(jsonPath("$.tracks[1].mine").value(false));

        add(token, bia, t(2, "Da Ana", CDN), "Bia").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Ana")));
        add(token, bia, t(1, "From the owner", CDN), "Bia").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Alex")));

        add(token, bia, t(3, "Sem nome", CDN), "").andExpect(status().isBadRequest());
        add(token, bia, t(3, "Sem nome", CDN), "   ").andExpect(status().isBadRequest());
        String longName = "B".repeat(60);
        add(token, bia, t(3, "Nome longo", CDN), longName).andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks[2].addedBy").value("B".repeat(40)));
        add(token, bia, t(4, "Name with tag", CDN), "<b>Bia</b>").andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks[3].addedBy").value("bBia/b"));

        add(token, bia, t(5, "Capa suspeita", "http://evil.example/track.png"), "Bia").andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks[4].cover").doesNotExist());
        add(token, bia, t(6, "Capa javascript", "javascript:alert(1)"), "Bia").andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks[5].cover").doesNotExist());
        add(token, bia, t(7, "Capa boa", CDN), "Bia").andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks[6].cover").value(CDN));

        remove(token, bia, 2).andExpect(status().isBadRequest());
        remove(token, ana, 1).andExpect(status().isBadRequest());
        remove(token, ana, 2).andExpect(status().isOk()).andExpect(jsonPath("$.tracks.length()").value(6));
        remove(token, ana, 999).andExpect(status().isNotFound());

        PlaylistView owner = service.get(id);
        assertEquals("owner", owner.tracks().get(0).addedById());
        assertEquals("Alex", owner.tracks().get(0).addedBy());
        assertTrue(owner.tracks().stream().anyMatch(x -> ("g:" + bia).equals(x.addedById())));
        assertTrue(owner.tracks()
                .stream()
                .filter(x -> x.addedById() != null && x.addedById().startsWith("g:"))
                .allMatch(x -> x.spotifyUri() == null),
                "guest tracks never keep the Spotify link they sent");
        assertTrue(owner.shared());
        assertNotNull(owner.tracks().get(1).addedAt());

        service.update(id, null, null, "Alex Morgan");
        info(token, ana).andExpect(jsonPath("$.tracks[0].addedBy").value("Alex Morgan"));

        sharing.disableShare(id);
        info(token, ana).andExpect(status().isNotFound());
        add(token, ana, t(8, "Tarde demais", CDN), "Ana").andExpect(status().isNotFound());
        String novo = sharing.enableShare(id);
        assertNotEquals(token, novo);
        assertTrue(novo.length() >= 24);
        info(token, ana).andExpect(status().isNotFound());
        info(novo, ana).andExpect(status().isOk());
        service.delete(id);
    }

    @Test
    void writesNeedAValidGuestIdAndReadsDoNot() throws Exception {
        Long id = service.create("Ids", null, null, "Owner").id();
        String token = sharing.enableShare(id);
        add(token, null, t(1, "X", CDN), "Ana").andExpect(status().isBadRequest());
        add(token, "short", t(1, "X", CDN), "Ana").andExpect(status().isBadRequest());
        add(token, "has spaces and symbols !!!", t(1, "X", CDN), "Ana").andExpect(status().isBadRequest());
        mvc.perform(delete("/api/guest/" + token + "/tracks/1").with(phone())).andExpect(status().isBadRequest());
        info(token, null).andExpect(status().isOk());
        info(token, "invalid!").andExpect(status().isOk());
        service.delete(id);
    }

    @Test
    void invalidOrShortTokensAreNotFound() throws Exception {
        for (String token : new String[]{"short", "tokeninvalido1234567890", "x".repeat(40)}) {
            info(token, gid("x")).andExpect(status().isNotFound());
            mvc.perform(get("/api/guest/" + token + "/search?q=abc").with(phone())).andExpect(status().isNotFound());
            mvc.perform(get("/api/guest/" + token + "/suggestions").with(phone())).andExpect(status().isNotFound());
        }
    }

    @Test
    void rateLimitReturns429AfterTooManyWrites() throws Exception {
        Long id = service.create("Limit", null, null, "Owner").id();
        String token = sharing.enableShare(id);
        String spammer = gid("spam");
        for (int i = 0; i < GuestRateLimiter.WRITES_PER_MINUTE; i++) {
            remove(token, spammer, 1000 + i).andExpect(status().isNotFound());
        }
        remove(token, spammer, 5000).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("wait")));
        add(token, spammer, t(1, "X", CDN), "Ana").andExpect(status().isTooManyRequests());
        add(token, gid("other"), t(1, "X", CDN), "Bia").andExpect(status().isOk());
        service.delete(id);
    }

    @Test
    void trackLimitPerPlaylistIsEnforced() {
        Long id = service.create("Full", null, null, "Owner").id();
        String token = sharing.enableShare(id);
        for (int i = 1; i <= GuestService.MAX_TRACKS; i++) {
            guests.addGuestTrack(token, t(i, "T" + i, CDN), "Ana", gid("lim"));
        }
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> guests.addGuestTrack(token, t(99_999, "Extra", CDN), "Ana", gid("lim")));
        assertTrue(e.getMessage().contains("limit"));
        service.delete(id);
    }


    @Test
    void ownerEndpointsForShareAndQrWork() throws Exception {
        Long id = service.create("Share", null, null, "Owner").id();
        RequestPostProcessor owner = r -> { r.setRemoteAddr("127.0.0.1"); r.addHeader("Host", "localhost:8080"); return r; };

        mvc.perform(get("/api/playlists/" + id + "/share").with(owner)).andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.token").doesNotExist());
        String token = om.readTree(mvc.perform(post("/api/playlists/" + id + "/share").with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true)).andReturn().getResponse().getContentAsString())
                        .get("token")
                        .asText();
        assertTrue(token.length() >= 24);
        mvc.perform(post("/api/playlists/" + id + "/share").with(owner)).andExpect(jsonPath("$.token").value(token));
        mvc.perform(get("/api/playlists/" + id + "/rev").with(owner)).andExpect(jsonPath("$.rev").isString());
        mvc.perform(get("/api/network").with(owner))
                .andExpect(jsonPath("$.port").isNumber())
                .andExpect(jsonPath("$.addresses").isArray())
                .andExpect(jsonPath("$.configured").doesNotExist());
        mvc.perform(get("/api/qr").param("text", "http://192.168.1.10:8080/join/" + token).with(owner))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("<svg")));
        mvc.perform(get("/api/qr").param("text", "x".repeat(501)).with(owner)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/qr").param("text", "  ").with(owner)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/playlists/" + id + "/share").with(owner))
                .andExpect(jsonPath("$.enabled").value(false));
        info(token, gid("x")).andExpect(status().isNotFound());

        String created = mvc.perform(post("/api/playlists").with(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\",\"ownerName\":\"  Alex  \"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Alex")).andReturn().getResponse().getContentAsString();
        long newId = om.readTree(created).get("id").asLong();
        String rev1 = service.rev(newId);
        service.addTrack(newId, t(1, "X", CDN));
        assertNotEquals(rev1, service.rev(newId));
        service.delete(newId);
        service.delete(id);
    }
}
