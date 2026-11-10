package com.mixtape.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:accessdb;DB_CLOSE_DELAY=-1", "server.port=0"})
@AutoConfigureMockMvc
class AccessFilterTest {
    @Autowired MockMvc mvc;

    private static RequestPostProcessor from(String ip) {
        return r -> { r.setRemoteAddr(ip); return r; };
    }

    @Test
    void ownerOnLoopbackWithLocalHostReachesEverything() throws Exception {
        mvc.perform(get("/api/playlists").header("Host", "localhost:8080").with(from("127.0.0.1")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/playlists").header("Host", "127.0.0.1:8080").with(from("127.0.0.1")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/playlists").header("Host", "[::1]:8080").with(from("0:0:0:0:0:0:0:1")))
                .andExpect(status().isOk());
    }

    @Test
    void remoteOriginCannotReachOwnerEndpointsOrTheOwnerApp() throws Exception {
        for (String path : new String[]{"/api/playlists", "/api/spotify/login", "/api/network", "/api/qr?text=x", "/auth/spotify/callback", "/app.js", "/js/main.js", "/index.html"}) {
            mvc.perform(get(path).header("Host", "192.168.1.10:8080").with(from("192.168.1.20")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/").header("Host", "192.168.1.10:8080").with(from("192.168.1.20")))
                .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Access restricted")));
        mvc.perform(get("/api/playlists").header("Host", "192.168.1.10:8080").with(from("192.168.1.20")))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void remoteOriginReachesGuestEndpointsAndTheJoinPage() throws Exception {
        mvc.perform(get("/api/guest/tokeninvalido123456/info")
                .header("Host", "192.168.1.10:8080")
                .with(from("192.168.1.20")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/guest/main.js").header("Host", "192.168.1.10:8080").with(from("192.168.1.20")))
                .andExpect(status().isOk());
        mvc.perform(get("/join/abc").header("Host", "192.168.1.10:8080").with(from("192.168.1.20")))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/join.html"));
    }

    @Test
    void loopbackThroughAProxyIsNotTheOwner() throws Exception {
        for (String h : new String[]{"X-Forwarded-For", "X-Forwarded-Host", "X-Real-IP", "Forwarded", "CF-Connecting-IP"}) {
            mvc.perform(get("/api/playlists")
                    .header("Host", "localhost:8080")
                    .header(h, "203.0.113.9")
                    .with(from("127.0.0.1")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void loopbackWithAForeignHostIsNotTheOwner() throws Exception {
        mvc.perform(get("/api/playlists").header("Host", "evil.com").with(from("127.0.0.1")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/playlists").header("Host", "evil.com:8080").with(from("127.0.0.1")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/playlists").header("Host", "localhost.evil.com").with(from("127.0.0.1")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/playlists").with(from("127.0.0.1"))).andExpect(status().isForbidden());
    }

    @Test
    void guestMayOpenOnlyTheGuestPagesAndRejectsTraversal() {
        assertTrue(AccessFilter.guestMayOpen("/join/abc"));
        assertTrue(AccessFilter.guestMayOpen("/join.html"));
        assertTrue(AccessFilter.guestMayOpen("/guest/main.js"));
        assertTrue(AccessFilter.guestMayOpen("/style.css"));
        assertTrue(AccessFilter.guestMayOpen("/icons/icon-192.png"));
        assertTrue(AccessFilter.guestMayOpen("/api/guest/abc/info"));
        assertTrue(AccessFilter.guestMayOpen("/api/preview/123"));

        assertFalse(AccessFilter.guestMayOpen("/"));
        assertFalse(AccessFilter.guestMayOpen("/app.js"));
        assertFalse(AccessFilter.guestMayOpen("/js/main.js"));
        assertFalse(AccessFilter.guestMayOpen("/guest/../js/main.js"));
        assertFalse(AccessFilter.guestMayOpen("/join.js"));
        assertFalse(AccessFilter.guestMayOpen("/index.html"));
        assertFalse(AccessFilter.guestMayOpen("/sw.js"));
        assertFalse(AccessFilter.guestMayOpen("/api/playlists"));
        assertFalse(AccessFilter.guestMayOpen("/auth/spotify/callback"));
        assertFalse(AccessFilter.guestMayOpen("/join/"));
        assertFalse(AccessFilter.guestMayOpen("/join/../app.js"));
        assertFalse(AccessFilter.guestMayOpen("/api/guest/x/../../playlists"));
        assertFalse(AccessFilter.guestMayOpen("/icons/..\\app.js"));
        assertFalse(AccessFilter.guestMayOpen("/join//x"));
        assertFalse(AccessFilter.guestMayOpen(null));
    }
}
