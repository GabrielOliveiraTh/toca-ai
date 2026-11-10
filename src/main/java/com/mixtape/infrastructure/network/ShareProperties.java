package com.mixtape.infrastructure.network;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.regex.Pattern;

@ConfigurationProperties("share")
public record ShareProperties(String host) {
    private static final Pattern VALID_HOST = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?");

    public ShareProperties {
        host = host == null || host.isBlank() ? null : host.trim();
        if (host != null && !VALID_HOST.matcher(host).matches()) {
            throw new IllegalArgumentException("Invalid share.host '" + host
                    + "': use only an IP address or a host name, without scheme, slash, space or port.");
        }
    }
}
