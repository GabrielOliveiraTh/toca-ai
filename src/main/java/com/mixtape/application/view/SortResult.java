package com.mixtape.application.view;

import java.util.Map;

public record SortResult(PlaylistView playlist, int withoutBpm, Map<Long, Double> bpm, int estimated) {
}
