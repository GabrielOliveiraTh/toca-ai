package com.mixtape.web.owner;

import com.mixtape.application.MoodSortService;
import com.mixtape.application.PlaylistService;
import com.mixtape.application.view.PlaylistView;
import com.mixtape.application.view.SortResult;
import com.mixtape.domain.Track;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TrackController {
    private final PlaylistService playlists;
    private final MoodSortService moodSort;

    public TrackController(PlaylistService playlists, MoodSortService moodSort) {
        this.playlists = playlists;
        this.moodSort = moodSort;
    }

    public record SortRequest(String mode) {
    }

    @PostMapping("/playlists/{id}/tracks")
    public PlaylistView add(@PathVariable long id, @RequestBody Track track) {
        return playlists.addTrack(id, track);
    }

    @DeleteMapping("/playlists/{id}/tracks/{deezerId}")
    public PlaylistView remove(@PathVariable long id, @PathVariable long deezerId) {
        return playlists.removeTrack(id, deezerId);
    }

    @PutMapping("/playlists/{id}/tracks")
    public PlaylistView replace(@PathVariable long id, @RequestBody List<Track> tracks) {
        return playlists.replaceTracks(id, tracks);
    }

    @PutMapping("/playlists/{id}/order")
    public PlaylistView order(@PathVariable long id, @RequestBody List<Long> ids) {
        return playlists.reorder(id, ids);
    }

    @PostMapping("/playlists/{id}/sort")
    public SortResult sort(@PathVariable long id, @RequestBody SortRequest request) {
        return moodSort.sortByMood(id, request.mode());
    }
}
