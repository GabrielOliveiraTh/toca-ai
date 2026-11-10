package com.mixtape.domain;

public final class Contributor {
    public static final String OWNER_ID = "owner";

    private static final String GUEST_PREFIX = "g:";

    private Contributor() {
    }

    public static String guestKey(String guestId) {
        return GUEST_PREFIX + guestId;
    }
}
