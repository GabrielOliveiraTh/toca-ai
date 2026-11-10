# Toca Aí

A study project built with Spring Boot: a local web app for building a playlist with friends and exporting it to Spotify.

It is not a product. I built it to practice Spring Boot 3, JPA with H2, calls to external APIs, concurrency, and a
ports-and-adapters layout (see [docs/architecture.md](docs/architecture.md)). The front end is plain HTML, CSS and
JavaScript modules, with no build step.

## What it does

- Create a playlist with a free-text style such as "Pagode anos 80" or "upbeat birthday songs from the 2000s" and get
  matching suggestions with 30-second previews.
- Compare it with a reference playlist and other playlists from Spotify, Deezer or YouTube.
- Share it through a QR code: friends open the link on their phones, enter a name, and add tracks.
- Export it to Spotify. Later exports update the same playlist.

## Requirements

- JDK 17 or newer (the Maven wrapper is included)

## Running

```bash
./mvnw spring-boot:run
```

Open http://127.0.0.1:8080. Use `127.0.0.1` or `localhost`; the owner interface is only served for those hosts.

Without configuration you can create playlists, search, play previews and get suggestions. Spotify and YouTube
features need the settings below.

## Configuration

Copy `application-local.example.yml` to `config/application.yml` and fill in your values. Settings can also come from
environment variables. `config/` and `data/` are ignored by git.

| Setting | Environment variable | Purpose |
|---|---|---|
| `spotify.client-id` | `SPOTIFY_CLIENT_ID` | Spotify login and export |
| `youtube.api-key` | `YOUTUBE_API_KEY` | YouTube playlist import and style search |
| `share.host` | `MIXTAPE_SHARE_HOST` | IP or host name used in the share link |
| `server.address` | `MIXTAPE_BIND` | Bind address, default `0.0.0.0` |

**Spotify:** create an app at https://developer.spotify.com/dashboard, add the Redirect URI
`http://127.0.0.1:8080/auth/spotify/callback`, and add your account under User Management while the app is in
development mode. Login uses OAuth with PKCE, so there is no client secret. The refresh token is stored in
`data/spotify-token.properties` and renewed automatically.

**Sharing:** the share link uses the first detected local address unless `share.host` is set. It must be an IP or host
name, without scheme, slash or port. To keep the app off the network, run with `MIXTAPE_BIND=127.0.0.1`.

## Security model

The app listens on the network so guests can reach the share link, but only the owner gets the full interface. A request
is the owner's only if it comes from loopback, has no proxy headers, and has a `Host` of `localhost`, `127.0.0.1` or
`[::1]`. Everyone else can reach only the guest page, the guest API, previews and a few static assets; the rest answers
403. Guests need the secret link, can remove only their own tracks, and are rate limited. Turning sharing off
invalidates the link immediately.

## Limitations

- Spotify returns tracks only for playlists you own or collaborate on. It no longer offers previews or recommendations
  to new apps, so those come from Deezer.
- The YouTube API has a daily quota, and compilation videos cannot be matched to single tracks.
- Sorting by mood uses BPM. Deezer rarely has it for recent releases, so it is estimated from the preview.
- The first style search can take 10 to 25 seconds.
- Spotify export and the service worker were tested only against fakes and simulators.

## Tests

```bash
./mvnw test
```
