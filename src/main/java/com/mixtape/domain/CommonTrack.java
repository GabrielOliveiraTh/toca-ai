package com.mixtape.domain;

import java.util.List;

public record CommonTrack(Track track, int count, List<String> sources, boolean inMine) {
}
