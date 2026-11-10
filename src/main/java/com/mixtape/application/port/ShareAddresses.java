package com.mixtape.application.port;

import java.util.List;

public interface ShareAddresses {
    List<Address> addresses();

    String configuredHost();

    record Address(String ip, String name) {
    }
}
