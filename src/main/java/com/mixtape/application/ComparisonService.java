package com.mixtape.application;

import com.mixtape.application.view.DiffView;
import com.mixtape.domain.CommonTrack;
import com.mixtape.domain.Playlist;
import com.mixtape.domain.PlaylistRepository;
import com.mixtape.domain.PlaylistTrack;
import com.mixtape.domain.SourcePlaylist;
import com.mixtape.domain.Track;
import com.mixtape.domain.TrackMatcher;
import com.mixtape.domain.error.BusinessRuleException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ComparisonService {
    private final PlaylistRepository repository;

    public ComparisonService(PlaylistRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public DiffView diff(long id, Long sourceId) {
        Playlist playlist = repository.require(id);
        SourcePlaylist source = findSource(playlist, sourceId)
                .orElseThrow(() -> new BusinessRuleException("Set a reference playlist to compare."));

        List<Track> mine = playlist.getTracks().stream().map(PlaylistTrack::toTrack).toList();
        Set<String> mineKeys = TrackMatcher.keysOf(mine);
        Set<String> sourceKeys = TrackMatcher.keysOf(source.getTracks());

        List<Track> both = new ArrayList<>();
        List<Track> onlyMine = new ArrayList<>();
        List<Track> onlySource = new ArrayList<>();
        for (Track track : mine) {
            (TrackMatcher.containedIn(track, sourceKeys) ? both : onlyMine).add(track);
        }
        for (Track track : source.getTracks()) {
            if (!TrackMatcher.containedIn(track, mineKeys)) {
                onlySource.add(track);
            }
        }
        return new DiffView(source.getId(), source.getName(), both, onlyMine, onlySource);
    }

    @Transactional(readOnly = true)
    public List<CommonTrack> common(long id) {
        Playlist playlist = repository.require(id);
        List<Track> mine = playlist.getTracks().stream().map(PlaylistTrack::toTrack).toList();
        return TrackMatcher.common(playlist.getSources(), mine);
    }

    private static Optional<SourcePlaylist> findSource(Playlist playlist, Long sourceId) {
        return playlist.getSources().stream()
                .filter(source -> sourceId != null ? source.getId().equals(sourceId) : source.isMain())
                .findFirst();
    }
}
