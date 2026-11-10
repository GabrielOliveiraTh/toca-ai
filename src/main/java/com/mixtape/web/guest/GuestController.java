package com.mixtape.web.guest;

import com.mixtape.application.GuestService;
import com.mixtape.application.SuggestionService;
import com.mixtape.application.port.TrackCatalog;
import com.mixtape.application.view.GuestView;
import com.mixtape.domain.SuggestionFilters;
import com.mixtape.domain.Track;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/guest/{token}")
public class GuestController {
    static final String HEADER = "X-Guest-Id";

    private static final Pattern GUEST_ID = Pattern.compile("^[A-Za-z0-9_-]{16,64}$");
    private static final int SEARCH_TEXT_MAX = 200;

    private final GuestService guests;
    private final SuggestionService suggestions;
    private final TrackCatalog catalog;
    private final GuestRateLimiter limiter;

    public GuestController(GuestService guests, SuggestionService suggestions, TrackCatalog catalog,
                           GuestRateLimiter limiter) {
        this.guests = guests;
        this.suggestions = suggestions;
        this.catalog = catalog;
        this.limiter = limiter;
    }

    public record AddRequest(Track track, String name) {
    }

    private static String requireId(String id) {
        if (id == null || !GUEST_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Guest identification missing or invalid.");
        }
        return id;
    }

    private static String optionalId(String id) {
        return id != null && GUEST_ID.matcher(id).matches() ? id : null;
    }

    @GetMapping("/info")
    public GuestView info(@PathVariable String token,
                          @RequestHeader(value = HEADER, required = false) String guestId) {
        return guests.guestView(token, optionalId(guestId));
    }

    @GetMapping("/search")
    public List<Track> search(@PathVariable String token, @RequestParam(defaultValue = "") String q,
                              @RequestHeader(value = HEADER, required = false) String guestId) {
        guests.playlistIdByToken(token);
        limiter.checkRead(String.valueOf(optionalId(guestId)));
        String text = q.trim();
        if (text.isEmpty()) {
            return List.of();
        }
        return catalog.search(text.length() > SEARCH_TEXT_MAX ? text.substring(0, SEARCH_TEXT_MAX) : text);
    }

    @GetMapping("/suggestions")
    public List<Track> suggestions(@PathVariable String token,
                                   @RequestHeader(value = HEADER, required = false) String guestId) {
        long playlistId = guests.playlistIdByToken(token);
        limiter.checkRead(String.valueOf(optionalId(guestId)));
        return suggestions.suggest(playlistId, SuggestionFilters.none());
    }

    @PostMapping("/tracks")
    public GuestView add(@PathVariable String token, @RequestBody AddRequest request,
                         @RequestHeader(value = HEADER, required = false) String guestId) {
        String id = requireId(guestId);
        guests.playlistIdByToken(token);
        limiter.checkWrite(id);
        return guests.addGuestTrack(token, request.track(), request.name(), id);
    }

    @DeleteMapping("/tracks/{deezerId}")
    public GuestView remove(@PathVariable String token, @PathVariable long deezerId,
                            @RequestHeader(value = HEADER, required = false) String guestId) {
        String id = requireId(guestId);
        guests.playlistIdByToken(token);
        limiter.checkWrite(id);
        return guests.removeGuestTrack(token, deezerId, id);
    }
}
