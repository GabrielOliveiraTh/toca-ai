package com.mixtape.application;

import com.mixtape.application.view.PlaylistSummary;
import com.mixtape.application.view.PlaylistView;
import com.mixtape.domain.Contributor;
import com.mixtape.domain.Playlist;
import com.mixtape.domain.PlaylistRepository;
import com.mixtape.domain.PlaylistTrack;
import com.mixtape.domain.TextSanitizer;
import com.mixtape.domain.Track;
import com.mixtape.domain.TrackMatcher;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PlaylistService {
    private static final String DEFAULT_NAME = "New playlist";

    private final PlaylistRepository repository;
    private final PlaylistViewAssembler assembler;
    private final SourceService sources;

    public PlaylistService(PlaylistRepository repository, PlaylistViewAssembler assembler, SourceService sources) {
        this.repository = repository;
        this.assembler = assembler;
        this.sources = sources;
    }

    @Transactional(readOnly = true)
    public List<PlaylistSummary> list() {
        return repository.findAll().stream()
                .map(playlist -> new PlaylistSummary(playlist.getId(), playlist.getName(),
                        playlist.getTracks().size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlaylistView get(long id) {
        return assembler.toView(repository.require(id));
    }

    @Transactional
    public PlaylistView create(String name, String referenceUrl, String style, String ownerName) {
        Playlist playlist = new Playlist();
        playlist.setName(name == null || name.isBlank() ? DEFAULT_NAME : name.trim());
        playlist.setOwnerName(TextSanitizer.personName(ownerName));
        playlist.setStyle(TextSanitizer.style(style));
        if (referenceUrl != null && !referenceUrl.isBlank()) {
            playlist.getSources().add(sources.importSource(playlist, referenceUrl.trim(), true));
        }
        repository.save(playlist);
        return assembler.toView(playlist);
    }

    @Transactional
    public PlaylistView update(long id, String name, String style, String ownerName) {
        Playlist playlist = repository.require(id);
        if (name != null && !name.isBlank()) {
            playlist.setName(name.trim());
        }
        if (style != null) {
            playlist.setStyle(TextSanitizer.style(style));
        }
        String owner = TextSanitizer.personName(ownerName);
        if (owner != null) {
            playlist.setOwnerName(owner);
        }
        return assembler.toView(playlist);
    }

    @Transactional
    public void delete(long id) {
        repository.deleteById(id);
    }

    @Transactional
    public PlaylistView addTrack(long id, Track track) {
        Playlist playlist = repository.require(id);
        boolean exists = playlist.getTracks().stream()
                .anyMatch(entity -> TrackMatcher.same(entity.toTrack(), track));
        if (!exists) {
            Track authored = track.withAdded(playlist.ownerLabel(), Instant.now(), Contributor.OWNER_ID);
            playlist.getTracks().add(PlaylistTrack.of(playlist, authored, playlist.getTracks().size()));
        }
        return assembler.toView(playlist);
    }

    @Transactional
    public PlaylistView removeTrack(long id, long deezerId) {
        Playlist playlist = repository.require(id);
        playlist.getTracks().removeIf(track -> track.getDeezerId() == deezerId);
        playlist.renumberTracks();
        return assembler.toView(playlist);
    }

    @Transactional
    public PlaylistView reorder(long id, List<Long> deezerIds) {
        Playlist playlist = repository.require(id);
        for (PlaylistTrack track : playlist.getTracks()) {
            int index = deezerIds.indexOf(track.getDeezerId());
            if (index >= 0) {
                track.setPosition(index);
            }
        }
        playlist.sortTracksByPosition();
        return assembler.toView(playlist);
    }

    @Transactional
    public PlaylistView replaceTracks(long id, List<Track> tracks) {
        Playlist playlist = repository.require(id);
        playlist.getTracks().clear();
        int position = 0;
        Set<Long> seen = new HashSet<>();
        for (Track track : tracks) {
            if (track.deezerId() == null || !seen.add(track.deezerId())) {
                continue;
            }
            playlist.getTracks().add(PlaylistTrack.of(playlist, track, position++));
        }
        return assembler.toView(playlist);
    }

    @Transactional(readOnly = true)
    public String rev(long id) {
        return repository.require(id).revision();
    }
}
