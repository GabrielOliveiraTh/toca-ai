package com.mixtape.web.owner;

import com.mixtape.application.port.TrackCatalog;
import com.mixtape.domain.Track;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final TrackCatalog catalog;

    public CatalogController(TrackCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/search")
    public List<Track> search(@RequestParam String q) {
        return q.isBlank() ? List.of() : catalog.search(q);
    }

    @GetMapping("/preview/{trackId}")
    public ResponseEntity<Void> preview(@PathVariable long trackId) {
        return catalog.previewUrl(trackId)
                .map(url -> ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, url)
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .<Void>build())
                .orElse(ResponseEntity.notFound().build());
    }
}
