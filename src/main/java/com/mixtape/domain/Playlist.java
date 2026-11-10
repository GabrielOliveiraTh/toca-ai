package com.mixtape.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Entity
public class Playlist {
    private static final String DEFAULT_OWNER_LABEL = "Owner";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(length = 300)
    private String style;

    @Column(length = 80)
    private String ownerName;

    @Column(unique = true, length = 64)
    private String shareToken;

    private String spotifyPlaylistId;

    @Column(length = 300)
    private String spotifyUrl;

    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Dislike> dislikes = new ArrayList<>();

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PlaylistTrack> tracks = new ArrayList<>();

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<SourcePlaylist> sources = new ArrayList<>();

    public String ownerLabel() {
        return ownerName != null ? ownerName : DEFAULT_OWNER_LABEL;
    }

    public void renumberTracks() {
        int position = 0;
        for (PlaylistTrack track : tracks) {
            track.setPosition(position++);
        }
    }

    public void sortTracksByPosition() {
        tracks.sort(Comparator.comparingInt(PlaylistTrack::getPosition));
    }

    public String revision() {
        List<String> parts = tracks.stream()
                .map(t -> t.getDeezerId() + ":" + t.getPosition() + ":" + t.getAddedBy())
                .toList();
        return Integer.toHexString(Objects.hash(name, ownerName, parts));
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getShareToken() { return shareToken; }
    public void setShareToken(String shareToken) { this.shareToken = shareToken; }
    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }
    public String getSpotifyPlaylistId() { return spotifyPlaylistId; }
    public void setSpotifyPlaylistId(String spotifyPlaylistId) { this.spotifyPlaylistId = spotifyPlaylistId; }
    public String getSpotifyUrl() { return spotifyUrl; }
    public void setSpotifyUrl(String spotifyUrl) { this.spotifyUrl = spotifyUrl; }
    public List<Dislike> getDislikes() { return dislikes; }
    public Instant getCreatedAt() { return createdAt; }
    public List<PlaylistTrack> getTracks() { return tracks; }
    public List<SourcePlaylist> getSources() { return sources; }
}
