package com.mixtape.application;

import com.mixtape.application.port.PlaylistImporter;
import com.mixtape.application.view.PlaylistView;
import com.mixtape.domain.Playlist;
import com.mixtape.domain.PlaylistRepository;
import com.mixtape.domain.SourcePlaylist;
import com.mixtape.domain.Track;
import com.mixtape.domain.error.BusinessRuleException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SourceService {
    private final PlaylistRepository repository;
    private final List<PlaylistImporter> importers;
    private final PlaylistViewAssembler assembler;

    public SourceService(PlaylistRepository repository, List<PlaylistImporter> importers,
                         PlaylistViewAssembler assembler) {
        this.repository = repository;
        this.importers = importers;
        this.assembler = assembler;
    }

    @Transactional
    public PlaylistView setReference(long id, String url) {
        Playlist playlist = repository.require(id);
        SourcePlaylist reference = url != null && !url.isBlank()
                ? importSource(playlist, url.trim(), true)
                : null;
        playlist.getSources().removeIf(SourcePlaylist::isMain);
        if (reference != null) {
            playlist.getSources().removeIf(source -> Objects.equals(source.getSourceKey(), reference.getSourceKey()));
            playlist.getSources().add(reference);
        }
        return assembler.toView(playlist);
    }

    @Transactional
    public PlaylistView addSource(long id, String url) {
        Playlist playlist = repository.require(id);
        String key = importerFor(url).sourceKey(url);
        boolean alreadyAdded = playlist.getSources().stream()
                .anyMatch(source -> Objects.equals(source.getSourceKey(), key));
        if (alreadyAdded) {
            throw new BusinessRuleException("This playlist was already added.");
        }
        playlist.getSources().add(importSource(playlist, url.trim(), false));
        return assembler.toView(playlist);
    }

    @Transactional
    public PlaylistView removeSource(long id, long sourceId) {
        Playlist playlist = repository.require(id);
        playlist.getSources().removeIf(source -> source.getId() == sourceId);
        return assembler.toView(playlist);
    }

    public SourcePlaylist importSource(Playlist playlist, String url, boolean main) {
        PlaylistImporter importer = importerFor(url);
        String key = importer.sourceKey(url);
        PlaylistImporter.ImportedPlaylist imported = importer.read(url);

        Map<Long, Track> unique = new LinkedHashMap<>();
        imported.tracks().forEach(track -> unique.putIfAbsent(track.deezerId(), track));
        if (unique.isEmpty()) {
            throw new BusinessRuleException(
                    "No tracks were identified in \"" + imported.name() + "\". If it is a YouTube playlist, "
                            + "it may contain long videos or compilations instead of individual songs.");
        }
        return SourcePlaylist.of(playlist, imported.name(), url, key, main, unique.values());
    }

    private PlaylistImporter importerFor(String url) {
        return importers.stream()
                .filter(importer -> importer.supports(url))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("Unsupported playlist link."));
    }
}
