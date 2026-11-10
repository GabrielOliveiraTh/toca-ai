# Architecture

One Maven module, organized as ports and adapters. Base package: `com.mixtape`.

## Packages

```
domain/           Playlist, PlaylistTrack, SourcePlaylist, Dislike, PlaylistRepository, Track, CommonTrack,
                  Contributor, TextSanitizer, TrackMatcher, MoodSorter, SuggestionFilters
  style/          StyleIntent, StyleVocabulary, StyleCandidate, CandidateGroup, StyleScorer
  error/          NotFoundException, BusinessRuleException
application/      use-case services, PlaylistViewAssembler, TrackResolver, DiscographyExpander, StyleWorkers, ...
  port/           interfaces implemented by infrastructure
  view/           read models serialized directly by the controllers
  support/        Parallel
infrastructure/   http, deezer, spotify, youtube, itunes, audio, qr, network
web/              AccessFilter, ApiExceptionHandler, PageController
  owner/          controllers for the owner API
  guest/          GuestController, GuestRateLimiter
```

## Dependency rule

- `domain` imports nothing from the other layers, and no Jackson or Spring. The exceptions are the
  `jakarta.persistence` annotations on entities and `PlaylistRepository extends JpaRepository`.
- `application` imports only `domain` (plus Spring for `@Service` and `@Transactional`).
- `infrastructure` imports `application` and `domain`.
- `web` imports `application` and `domain`, never `infrastructure`.

## Ports

All in `application.port`:

- `TrackCatalog`: search, match by ISRC or title, preview URL and bytes
- `ArtistCatalog`: artist lookup, top tracks, related artists, albums
- `TrackMetadata`: BPM, genres and release year of a track
- `TempoEstimator`: BPM estimated from a preview
- `PlaylistImporter`: reads a playlist from a URL (Deezer, YouTube, Spotify)
- `PlaylistExporter`: exports to Spotify
- `StyleCandidateSource`: one source of the style funnel (Deezer, iTunes, YouTube, Spotify)
- `MusicAccount`: connect and disconnect the Spotify account
- `ShareAddresses`: addresses offered for the share link
- `QrCodes`: QR code as SVG

Adding a playlist source or a style source means writing one class that implements the port; services receive them as
ordered lists.

## Concurrency rules

1. No network call inside `computeIfAbsent`. A thread waiting on the network can run another task on top of its own
   stack that asks for the same map lock, which deadlocks. Use `get`, query, then `putIfAbsent`.
2. No `parallelStream` for I/O. Use `Parallel.map` and `Parallel.flatMap`, which run on a dedicated pool with a time
   limit, for the same deadlock reason.
3. Every `RestClient` comes from `Http.client`, so every call has connect and read timeouts and its own executor, and a
   silent server fails fast instead of starving the pool.
4. Caches only store conclusive answers. A network failure is not cached, otherwise one hiccup would stick as "unknown".
