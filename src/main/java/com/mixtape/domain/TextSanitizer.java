package com.mixtape.domain;

public final class TextSanitizer {
    private static final int PERSON_NAME_MAX = 40;
    private static final int STYLE_MAX = 200;

    private TextSanitizer() {
    }

    public static String personName(String name) {
        if (name == null) {
            return null;
        }
        String cleaned = name.replaceAll("[\\p{Cntrl}<>]", "").trim().replaceAll("\\s{2,}", " ");
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > PERSON_NAME_MAX ? cleaned.substring(0, PERSON_NAME_MAX).trim() : cleaned;
    }

    public static String style(String style) {
        if (style == null) {
            return null;
        }
        String cleaned = style.trim().replaceAll("\\s{2,}", " ");
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > STYLE_MAX ? cleaned.substring(0, STYLE_MAX) : cleaned;
    }

    public static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replaceAll("\\p{Cntrl}", "").trim();
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }

    // Covers only from the Deezer CDN, so a guest cannot make the owner browser load other addresses.
    public static String coverUrl(String url) {
        boolean fromDeezerCdn = url != null && url.matches("https://[a-z0-9.-]*dzcdn\\.net/[^\\s'\"()]*");
        return fromDeezerCdn ? url : null;
    }
}
