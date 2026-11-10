package com.mixtape.application;

import com.mixtape.application.view.PlaylistView;
import com.mixtape.domain.Dislike;
import com.mixtape.domain.Playlist;
import com.mixtape.domain.PlaylistRepository;
import com.mixtape.domain.Track;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DislikeService {
    private final PlaylistRepository repository;
    private final PlaylistViewAssembler assembler;

    public DislikeService(PlaylistRepository repository, PlaylistViewAssembler assembler) {
        this.repository = repository;
        this.assembler = assembler;
    }

    @Transactional
    public PlaylistView addDislike(long id, Track track) {
        Playlist playlist = repository.require(id);
        boolean alreadyDisliked = track.deezerId() == null
                || playlist.getDislikes().stream().anyMatch(d -> track.deezerId().equals(d.getDeezerId()));
        if (!alreadyDisliked) {
            playlist.getDislikes().add(Dislike.of(playlist, track));
        }
        return assembler.toView(playlist);
    }

    @Transactional
    public PlaylistView removeDislike(long id, long deezerId) {
        Playlist playlist = repository.require(id);
        playlist.getDislikes().removeIf(d -> d.getDeezerId() != null && d.getDeezerId() == deezerId);
        return assembler.toView(playlist);
    }

    @Transactional(readOnly = true)
    public List<Track> dislikes(long id) {
        return repository.require(id).getDislikes().stream().map(Dislike::toTrack).toList();
    }
}
