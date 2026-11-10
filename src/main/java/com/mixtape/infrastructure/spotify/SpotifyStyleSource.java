package com.mixtape.infrastructure.spotify;

import com.mixtape.application.port.StyleCandidateSource;
import com.mixtape.domain.style.StyleCandidate;
import com.mixtape.domain.style.StyleIntent;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(4)
public class SpotifyStyleSource implements StyleCandidateSource {
    private static final int PAGE_SIZE = 10;
    private static final int GENRE_PAGES = 3;
    private static final int GENRE_QUERIES = 2;

    private final SpotifyAuth auth;
    private final SpotifyApi api;

    SpotifyStyleSource(SpotifyAuth auth, SpotifyApi api) {
        this.auth = auth;
        this.api = api;
    }

    @Override
    public String key() {
        return "spotify";
    }

    @Override
    public String displayName() {
        return "Spotify";
    }

    @Override
    public boolean available() {
        return auth.connected();
    }

    @Override
    public List<StyleCandidate> candidates(StyleIntent intent) {
        String yearFilter = "";
        if (intent.hasYears()) {
            boolean singleYear = intent.yearTo().equals(intent.yearFrom());
            yearFilter = " year:" + intent.yearFrom() + (singleYear ? "" : "-" + intent.yearTo());
        }
        Map<String, Integer> queries = new LinkedHashMap<>();
        for (String genre : intent.genres().stream().limit(GENRE_QUERIES).toList()) {
            queries.put("genre:\"" + genre + "\"" + yearFilter, GENRE_PAGES);
        }
        if (!intent.terms().isEmpty()) {
            queries.put(String.join(" ", intent.terms()) + yearFilter, intent.genres().isEmpty() ? 3 : 2);
        }
        List<StyleCandidate> out = new ArrayList<>();
        queries.forEach((query, pages) -> {
            for (int page = 0; page < pages; page++) {
                try {
                    List<StyleCandidate> hits = search(query, page * PAGE_SIZE);
                    out.addAll(hits);
                    if (hits.size() < PAGE_SIZE) {
                        break;
                    }
                } catch (RuntimeException e) {
                    break;
                }
            }
        });
        return out;
    }

    private List<StyleCandidate> search(String query, int offset) {
        List<StyleCandidate> hits = new ArrayList<>();
        JsonNode result = api.get("/search?q={q}&type=track&limit=" + PAGE_SIZE + "&offset={o}", query, offset);
        for (JsonNode track : result.path("tracks").path("items")) {
            if (!track.hasNonNull("uri")) {
                continue;
            }
            String date = track.path("album").path("release_date").asText("");
            Integer year = date.length() >= 4 && date.substring(0, 4).matches("\\d{4}")
                    ? Integer.valueOf(date.substring(0, 4))
                    : null;
            hits.add(new StyleCandidate(
                    track.path("artists").path(0).path("name").asText(),
                    track.path("name").asText(),
                    year, null,
                    track.path("external_ids").path("isrc").asText(null),
                    track.get("uri").asText(),
                    null, "spotify", false));
        }
        return hits;
    }
}
