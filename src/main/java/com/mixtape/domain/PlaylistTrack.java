package com.mixtape.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

@Entity
public class PlaylistTrack {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Playlist playlist;

    private int position;
    private Long deezerId;

    @Column(length = 500)
    private String title;

    @Column(length = 500)
    private String artist;

    private Long artistId;

    @Column(length = 500)
    private String album;

    @Column(length = 500)
    private String cover;

    private String isrc;
    private String spotifyUri;

    @Column(length = 80)
    private String addedBy;

    private Instant addedAt;

    @Column(length = 80)
    private String addedById;

    public static PlaylistTrack of(Playlist playlist, Track track, int position) {
        PlaylistTrack entity = new PlaylistTrack();
        entity.playlist = playlist;
        entity.position = position;
        entity.deezerId = track.deezerId();
        entity.title = track.title();
        entity.artist = track.artist();
        entity.artistId = track.artistId();
        entity.album = track.album();
        entity.cover = track.cover();
        entity.isrc = track.isrc();
        entity.spotifyUri = track.spotifyUri();
        entity.addedBy = track.addedBy();
        entity.addedAt = track.addedAt();
        entity.addedById = track.addedById();
        return entity;
    }

    public Track toTrack() {
        return new Track(deezerId, title, artist, artistId, album, cover, isrc, spotifyUri, null,
                addedBy, addedAt, addedById);
    }

    public Long getId() { return id; }
    public Playlist getPlaylist() { return playlist; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public Long getDeezerId() { return deezerId; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public Long getArtistId() { return artistId; }
    public String getAlbum() { return album; }
    public String getCover() { return cover; }
    public String getIsrc() { return isrc; }
    public String getSpotifyUri() { return spotifyUri; }
    public String getAddedBy() { return addedBy; }
    public Instant getAddedAt() { return addedAt; }
    public String getAddedById() { return addedById; }
}
