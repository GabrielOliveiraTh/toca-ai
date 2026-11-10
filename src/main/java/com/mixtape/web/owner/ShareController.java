package com.mixtape.web.owner;

import com.mixtape.application.SharingService;
import com.mixtape.application.port.QrCodes;
import com.mixtape.application.port.ShareAddresses;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ShareController {
    private static final int QR_TEXT_MAX = 500;

    private final SharingService sharing;
    private final ShareAddresses addresses;
    private final QrCodes qrCodes;
    private final int port;

    public ShareController(SharingService sharing, ShareAddresses addresses, QrCodes qrCodes,
                           @Value("${server.port:8080}") int port) {
        this.sharing = sharing;
        this.addresses = addresses;
        this.qrCodes = qrCodes;
        this.port = port;
    }

    public record ShareState(boolean enabled, String token) {
        static ShareState of(String token) {
            return new ShareState(token != null, token);
        }
    }

    public record NetworkResponse(int port, List<ShareAddresses.Address> addresses, String configured) {
    }

    @GetMapping("/playlists/{id}/share")
    public ShareState status(@PathVariable long id) {
        return ShareState.of(sharing.shareToken(id).orElse(null));
    }

    @PostMapping("/playlists/{id}/share")
    public ShareState enable(@PathVariable long id) {
        return ShareState.of(sharing.enableShare(id));
    }

    @DeleteMapping("/playlists/{id}/share")
    public ShareState disable(@PathVariable long id) {
        sharing.disableShare(id);
        return ShareState.of(null);
    }

    @GetMapping("/network")
    public NetworkResponse network() {
        return new NetworkResponse(port, addresses.addresses(), addresses.configuredHost());
    }

    @GetMapping(value = "/qr", produces = "image/svg+xml")
    public ResponseEntity<String> qrCode(@RequestParam String text) {
        if (text.isBlank() || text.length() > QR_TEXT_MAX) {
            throw new IllegalArgumentException("Invalid QR code text (up to 500 characters).");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("image/svg+xml"))
                .body(qrCodes.svg(text));
    }
}
