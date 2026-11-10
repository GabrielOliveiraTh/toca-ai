package com.mixtape.infrastructure.itunes;

import com.mixtape.infrastructure.http.Http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class ItunesClient {
    record Hit(String title, String artist, Integer year, String genre) {
    }

    private final RestClient http = Http.client("https://itunes.apple.com");
    private final ObjectMapper mapper = new ObjectMapper();

    List<Hit> search(String term, int limit) {
        List<Hit> out = new ArrayList<>();
        try {
            String body = http.get()
                    .uri("/search?term={t}&media=music&entity=song&country=br&limit={l}", term, limit)
                    .retrieve()
                    .body(String.class);
            for (JsonNode result : mapper.readTree(body).path("results")) {
                String title = result.path("trackName").asText("");
                String artist = result.path("artistName").asText("");
                if (!title.isBlank() && !artist.isBlank()) {
                    out.add(new Hit(title, artist, releaseYear(result.path("releaseDate").asText("")),
                            result.path("primaryGenreName").asText("")));
                }
            }
        } catch (Exception e) {
            return out;
        }
        return out;
    }

    private static Integer releaseYear(String date) {
        boolean hasYear = date.length() >= 4 && date.substring(0, 4).matches("\\d{4}");
        return hasYear ? Integer.valueOf(date.substring(0, 4)) : null;
    }
}
