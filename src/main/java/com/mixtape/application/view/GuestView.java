package com.mixtape.application.view;

import java.time.Instant;
import java.util.List;

public record GuestView(String playlistName, String ownerName, String style, String rev, List<GuestTrack> tracks) {
    public record GuestTrack(Long deezerId, String title, String artist, String album, String cover,
                             String addedBy, Instant addedAt, boolean byOwner, boolean mine) {
    }
}
