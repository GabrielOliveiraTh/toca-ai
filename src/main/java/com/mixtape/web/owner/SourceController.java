package com.mixtape.web.owner;

import com.mixtape.application.ComparisonService;
import com.mixtape.application.SourceService;
import com.mixtape.application.view.DiffView;
import com.mixtape.application.view.PlaylistView;
import com.mixtape.domain.CommonTrack;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SourceController {
    private final SourceService sources;
    private final ComparisonService comparison;

    public SourceController(SourceService sources, ComparisonService comparison) {
        this.sources = sources;
        this.comparison = comparison;
    }

    public record UrlRequest(String url) {
    }

    @PutMapping("/playlists/{id}/reference")
    public PlaylistView reference(@PathVariable long id, @RequestBody UrlRequest request) {
        return sources.setReference(id, request.url());
    }

    @PostMapping("/playlists/{id}/sources")
    public PlaylistView addSource(@PathVariable long id, @RequestBody UrlRequest request) {
        return sources.addSource(id, request.url());
    }

    @DeleteMapping("/playlists/{id}/sources/{sourceId}")
    public PlaylistView removeSource(@PathVariable long id, @PathVariable long sourceId) {
        return sources.removeSource(id, sourceId);
    }

    @GetMapping("/playlists/{id}/common")
    public List<CommonTrack> common(@PathVariable long id) {
        return comparison.common(id);
    }

    @GetMapping("/playlists/{id}/diff")
    public DiffView diff(@PathVariable long id, @RequestParam(required = false) Long against) {
        return comparison.diff(id, against);
    }
}
