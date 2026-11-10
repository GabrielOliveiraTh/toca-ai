package com.mixtape.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Dislike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Playlist playlist;

    private Long deezerId;
    private Long artistId;

    @Column(length = 500)
    private String artist;

    @Column(length = 500)
    private String title;

    private String isrc;

    public static Dislike of(Playlist playlist, Track track) {
        Dislike dislike = new Dislike();
        dislike.playlist = playlist;
        dislike.deezerId = track.deezerId();
        dislike.artistId = track.artistId();
        dislike.artist = track.artist();
        dislike.title = track.title();
        dislike.isrc = track.isrc();
        return dislike;
    }

    public Track toTrack() {
        return Track.of(deezerId, title, artist, artistId, null, null, isrc);
    }

    public Long getId() { return id; }
    public Playlist getPlaylist() { return playlist; }
    public Long getDeezerId() { return deezerId; }
    public Long getArtistId() { return artistId; }
    public String getArtist() { return artist; }
    public String getTitle() { return title; }
    public String getIsrc() { return isrc; }
}
