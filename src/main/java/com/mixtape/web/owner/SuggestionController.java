package com.mixtape.web.owner;

import com.mixtape.application.DislikeService;
import com.mixtape.application.GenreFilter;
import com.mixtape.application.StyleSuggestionService;
import com.mixtape.application.SuggestionService;
import com.mixtape.application.view.PlaylistView;
import com.mixtape.domain.SuggestionFilters;
import com.mixtape.domain.Track;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SuggestionController {
    private final SuggestionService suggestions;
    private final StyleSuggestionService styleSuggestions;
    private final DislikeService dislikes;

    public SuggestionController(SuggestionService suggestions, StyleSuggestionService styleSuggestions,
                                DislikeService dislikes) {
        this.suggestions = suggestions;
        this.styleSuggestions = styleSuggestions;
        this.dislikes = dislikes;
    }

    @GetMapping("/playlists/{id}/suggestions")
    public List<Track> suggestions(@PathVariable long id,
                                   @RequestParam(defaultValue = "false") boolean avoidLive,
                                   @RequestParam(defaultValue = "false") boolean avoidRemix,
                                   @RequestParam(defaultValue = "false") boolean popular,
                                   @RequestParam(defaultValue = "") String excludeGenres) {
        SuggestionFilters filters =
                new SuggestionFilters(avoidLive, avoidRemix, popular, GenreFilter.parse(excludeGenres));
        return suggestions.suggest(id, filters);
    }

    @GetMapping("/style/sources")
    public List<String> styleSources() {
        return styleSuggestions.activeSources();
    }

    @GetMapping("/playlists/{id}/dislikes")
    public List<Track> dislikes(@PathVariable long id) {
        return dislikes.dislikes(id);
    }

    @PostMapping("/playlists/{id}/dislikes")
    public PlaylistView dislike(@PathVariable long id, @RequestBody Track track) {
        return dislikes.addDislike(id, track);
    }

    @DeleteMapping("/playlists/{id}/dislikes/{deezerId}")
    public PlaylistView undislike(@PathVariable long id, @PathVariable long deezerId) {
        return dislikes.removeDislike(id, deezerId);
    }
}
