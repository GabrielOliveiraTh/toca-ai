package com.mixtape.application.view;

import com.mixtape.domain.Track;

import java.util.List;

public record DiffView(Long sourceId, String sourceName,
                       List<Track> both, List<Track> onlyMine, List<Track> onlySource) {
}
