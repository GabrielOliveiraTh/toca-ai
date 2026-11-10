package com.mixtape.web.owner;

import com.mixtape.application.ExportService;
import com.mixtape.application.PlaylistService;
import com.mixtape.application.port.PlaylistExporter;
import com.mixtape.application.view.PlaylistSummary;
import com.mixtape.application.view.PlaylistView;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PlaylistController {
    private final PlaylistService playlists;
    private final ExportService exports;

    public PlaylistController(PlaylistService playlists, ExportService exports) {
        this.playlists = playlists;
        this.exports = exports;
    }

    public record CreateRequest(String name, String referenceUrl, String style, String ownerName) {
    }

    public record UpdateRequest(String name, String style, String ownerName) {
    }

    public record RevisionResponse(String rev) {
    }

    @GetMapping("/playlists")
    public List<PlaylistSummary> list() {
        return playlists.list();
    }

    @PostMapping("/playlists")
    public PlaylistView create(@RequestBody CreateRequest request) {
        return playlists.create(request.name(), request.referenceUrl(), request.style(), request.ownerName());
    }

    @GetMapping("/playlists/{id}")
    public PlaylistView get(@PathVariable long id) {
        return playlists.get(id);
    }

    @PatchMapping("/playlists/{id}")
    public PlaylistView update(@PathVariable long id, @RequestBody UpdateRequest request) {
        return playlists.update(id, request.name(), request.style(), request.ownerName());
    }

    @DeleteMapping("/playlists/{id}")
    public void delete(@PathVariable long id) {
        playlists.delete(id);
    }

    @GetMapping("/playlists/{id}/rev")
    public RevisionResponse revision(@PathVariable long id) {
        return new RevisionResponse(playlists.rev(id));
    }

    @PostMapping("/playlists/{id}/export")
    public PlaylistExporter.ExportResult export(@PathVariable long id) {
        return exports.export(id);
    }
}
