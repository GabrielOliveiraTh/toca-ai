package com.mixtape.application;

import com.mixtape.application.port.TrackCatalog;
import com.mixtape.domain.Track;
import com.mixtape.domain.error.BusinessRuleException;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
public class TrackResolver {
    // Few threads, because of the catalog request limit.
    private static final int THREADS = 3;

    public record Request(String isrc, String title, String artist, String spotifyUri) {
        public static Request of(String isrc, String title, String artist) {
            return new Request(isrc, title, artist, null);
        }
    }

    private final TrackCatalog catalog;

    public TrackResolver(TrackCatalog catalog) {
        this.catalog = catalog;
    }

    public List<Track> resolve(List<Request> requests) {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Track>> futures = requests.stream()
                    .map(request -> pool.submit(lookup(request)))
                    .toList();
            List<Track> found = new ArrayList<>();
            for (Future<Track> future : futures) {
                Track track = future.get();
                if (track != null) {
                    found.add(track);
                }
            }
            return found;
        } catch (InterruptedException | ExecutionException e) {
            throw new BusinessRuleException("Falha ao importar a playlist.");
        } finally {
            pool.shutdown();
        }
    }

    private Callable<Track> lookup(Request request) {
        return () -> catalog.match(request.isrc(), request.title(), request.artist())
                .map(track -> request.spotifyUri() == null ? track : track.withSpotifyUri(request.spotifyUri()))
                .orElse(null);
    }
}
