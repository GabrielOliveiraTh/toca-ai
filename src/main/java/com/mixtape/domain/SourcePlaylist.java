package com.mixtape.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
public class SourcePlaylist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Playlist playlist;

    private String name;

    @Column(length = 500)
    private String url;

    private String sourceKey;

    @Column(name = "is_main")
    private boolean main;

    @ElementCollection
    @CollectionTable(name = "source_playlist_track")
    @OrderColumn
    private List<Track> tracks = new ArrayList<>();

    public static SourcePlaylist of(Playlist playlist, String name, String url, String sourceKey, boolean main,
                                    Collection<Track> tracks) {
        SourcePlaylist source = new SourcePlaylist();
        source.playlist = playlist;
        source.name = name;
        source.url = url;
        source.sourceKey = sourceKey;
        source.main = main;
        source.tracks.addAll(tracks);
        return source;
    }

    public Long getId() { return id; }
    public Playlist getPlaylist() { return playlist; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public String getSourceKey() { return sourceKey; }
    public boolean isMain() { return main; }
    public List<Track> getTracks() { return tracks; }
}
