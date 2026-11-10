package com.mixtape.application;

import com.mixtape.application.view.GuestView;
import com.mixtape.application.view.PlaylistView;
import com.mixtape.domain.Contributor;
import com.mixtape.domain.Playlist;
import com.mixtape.domain.PlaylistTrack;
import com.mixtape.domain.SourcePlaylist;
import com.mixtape.domain.Track;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class PlaylistViewAssembler {
    public PlaylistView toView(Playlist playlist) {
        List<PlaylistView.SourceView> sources = playlist.getSources().stream()
                .sorted(Comparator.comparing(SourcePlaylist::isMain).reversed())
                .map(source -> new PlaylistView.SourceView(source.getId(), source.getName(), source.getUrl(),
                        source.isMain(), List.copyOf(source.getTracks())))
                .toList();
        List<Track> tracks = playlist.getTracks().stream()
                .map(track -> withAuthorship(playlist, track))
                .toList();
        return new PlaylistView(playlist.getId(), playlist.getName(), playlist.getStyle(), playlist.getOwnerName(),
                playlist.getShareToken() != null, playlist.getSpotifyUrl(), playlist.getDislikes().size(), tracks,
                sources);
    }

    public GuestView toGuestView(Playlist playlist, String guestId) {
        String me = guestId == null ? null : Contributor.guestKey(guestId);
        List<GuestView.GuestTrack> tracks = playlist.getTracks().stream().map(entity -> {
            Track track = withAuthorship(playlist, entity);
            boolean byOwner = Contributor.OWNER_ID.equals(entity.getAddedById());
            boolean mine = me != null && me.equals(entity.getAddedById());
            return new GuestView.GuestTrack(track.deezerId(), track.title(), track.artist(), track.album(),
                    track.cover(), track.addedBy(), track.addedAt(), byOwner, mine);
        }).toList();
        return new GuestView(playlist.getName(), playlist.ownerLabel(), playlist.getStyle(), playlist.revision(),
                tracks);
    }

    public Track withAuthorship(Playlist playlist, PlaylistTrack entity) {
        Track track = entity.toTrack();
        if (Contributor.OWNER_ID.equals(entity.getAddedById())) {
            return track.withAdded(playlist.ownerLabel(), entity.getAddedAt(), Contributor.OWNER_ID);
        }
        return track;
    }
}
