package com.mixtape.infrastructure.deezer;

import com.mixtape.domain.Track;

import com.fasterxml.jackson.databind.JsonNode;

final class DeezerTrackMapper {
    private DeezerTrackMapper() {
    }

    static Track toTrack(JsonNode node) {
        JsonNode album = node.path("album");
        int rank = node.path("rank").asInt(0);
        return new Track(
                node.path("id").asLong(),
                node.path("title").asText(),
                node.path("artist").path("name").asText(),
                node.path("artist").path("id").asLong(),
                album.path("title").asText(null),
                album.path("cover_medium").asText(null),
                node.path("isrc").asText(null),
                null,
                rank > 0 ? rank : null,
                null,
                null,
                null);
    }
}
