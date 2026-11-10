package com.mixtape.application;

import com.mixtape.application.port.PlaylistExporter;
import com.mixtape.domain.Playlist;
import com.mixtape.domain.PlaylistRepository;
import com.mixtape.domain.PlaylistTrack;
import com.mixtape.domain.Track;
import com.mixtape.domain.error.BusinessRuleException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExportService {
    private final PlaylistRepository repository;
    private final PlaylistExporter exporter;

    public ExportService(PlaylistRepository repository, PlaylistExporter exporter) {
        this.repository = repository;
        this.exporter = exporter;
    }

    @Transactional
    public PlaylistExporter.ExportResult export(long id) {
        Playlist playlist = repository.require(id);
        List<Track> tracks = playlist.getTracks().stream().map(PlaylistTrack::toTrack).toList();
        if (tracks.isEmpty()) {
            throw new BusinessRuleException("The playlist is empty.");
        }
        PlaylistExporter.ExportResult result =
                exporter.export(playlist.getName(), tracks, playlist.getSpotifyPlaylistId());
        playlist.setSpotifyPlaylistId(result.playlistId());
        playlist.setSpotifyUrl(result.url());
        return result;
    }
}
