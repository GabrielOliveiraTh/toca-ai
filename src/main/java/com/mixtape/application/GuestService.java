package com.mixtape.application;

import com.mixtape.application.view.GuestView;
import com.mixtape.domain.Contributor;
import com.mixtape.domain.Playlist;
import com.mixtape.domain.PlaylistRepository;
import com.mixtape.domain.PlaylistTrack;
import com.mixtape.domain.TextSanitizer;
import com.mixtape.domain.Track;
import com.mixtape.domain.TrackMatcher;
import com.mixtape.domain.error.BusinessRuleException;
import com.mixtape.domain.error.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class GuestService {
    public static final int MAX_TRACKS = 500;

    private static final int TEXT_MAX = 300;
    private static final int ISRC_MAX = 20;

    private final PlaylistRepository repository;
    private final PlaylistViewAssembler assembler;

    public GuestService(PlaylistRepository repository, PlaylistViewAssembler assembler) {
        this.repository = repository;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public long playlistIdByToken(String token) {
        return repository.requireByShareToken(token).getId();
    }

    @Transactional(readOnly = true)
    public GuestView guestView(String token, String guestId) {
        return assembler.toGuestView(repository.requireByShareToken(token), guestId);
    }

    @Transactional
    public GuestView addGuestTrack(String token, Track track, String name, String guestId) {
        Playlist playlist = repository.requireByShareToken(token);
        String who = TextSanitizer.personName(name);
        if (who == null) {
            throw new BusinessRuleException("Enter your name to add tracks.");
        }
        if (track == null || track.deezerId() == null || track.title() == null || track.title().isBlank()) {
            throw new BusinessRuleException("Invalid track.");
        }
        if (playlist.getTracks().size() >= MAX_TRACKS) {
            throw new BusinessRuleException("The playlist reached the limit of " + MAX_TRACKS + " tracks.");
        }
        Optional<PlaylistTrack> duplicate = playlist.getTracks().stream()
                .filter(entity -> TrackMatcher.same(entity.toTrack(), track))
                .findFirst();
        if (duplicate.isPresent()) {
            String addedBy = assembler.withAuthorship(playlist, duplicate.get()).addedBy();
            throw new BusinessRuleException("This track is already in the playlist (added by " + addedBy + ").");
        }

        // Only the catalog fields are trusted; authorship, Spotify link and cover come from the server.
        Track clean = Track.of(track.deezerId(),
                        TextSanitizer.truncate(track.title(), TEXT_MAX),
                        TextSanitizer.truncate(track.artist(), TEXT_MAX),
                        track.artistId(),
                        TextSanitizer.truncate(track.album(), TEXT_MAX),
                        TextSanitizer.coverUrl(track.cover()),
                        TextSanitizer.truncate(track.isrc(), ISRC_MAX))
                .withRank(track.rank())
                .withAdded(who, Instant.now(), Contributor.guestKey(guestId));
        playlist.getTracks().add(PlaylistTrack.of(playlist, clean, playlist.getTracks().size()));
        return assembler.toGuestView(playlist, guestId);
    }

    @Transactional
    public GuestView removeGuestTrack(String token, long deezerId, String guestId) {
        Playlist playlist = repository.requireByShareToken(token);
        PlaylistTrack entity = playlist.getTracks().stream()
                .filter(track -> track.getDeezerId() != null && track.getDeezerId() == deezerId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Track not found in the playlist."));
        if (!Contributor.guestKey(guestId).equals(entity.getAddedById())) {
            throw new BusinessRuleException("You can only remove tracks you added yourself.");
        }
        playlist.getTracks().remove(entity);
        playlist.renumberTracks();
        return assembler.toGuestView(playlist, guestId);
    }
}
