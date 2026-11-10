package com.mixtape.infrastructure.network;

import com.mixtape.application.port.ShareAddresses;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Component
public class LocalShareAddresses implements ShareAddresses {
    private static final String CONFIGURED_NAME = "Configured";

    private final ShareProperties properties;
    private final Supplier<List<NetworkInfo.Address>> detector;

    @Autowired
    public LocalShareAddresses(ShareProperties properties) {
        this(properties, NetworkInfo::addresses);
    }

    LocalShareAddresses(ShareProperties properties, Supplier<List<NetworkInfo.Address>> detector) {
        this.properties = properties;
        this.detector = detector;
    }

    @Override
    public List<Address> addresses() {
        List<Address> out = new ArrayList<>();
        String configured = configuredHost();
        if (configured != null) {
            out.add(new Address(configured, CONFIGURED_NAME));
        }
        for (NetworkInfo.Address detected : detector.get()) {
            if (!detected.ip().equals(configured)) {
                out.add(new Address(detected.ip(), detected.name()));
            }
        }
        return out;
    }

    @Override
    public String configuredHost() {
        return properties.host();
    }
}
