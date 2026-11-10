package com.mixtape.domain.style;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public final class StyleVocabulary {
    private static final String RESOURCE = "/style-vocabulary.properties";
    private static final String GENRE_PREFIX = "genre.";
    private static final String MOOD_PREFIX = "mood.";

    private static final StyleVocabulary INSTANCE = load();

    private final Set<String> stopwords;
    private final Map<String, String> genres;
    private final Map<String, List<String>> moods;

    private StyleVocabulary(Set<String> stopwords, Map<String, String> genres, Map<String, List<String>> moods) {
        this.stopwords = stopwords;
        this.genres = genres;
        this.moods = moods;
    }

    public static StyleVocabulary get() {
        return INSTANCE;
    }

    public Set<String> stopwords() {
        return stopwords;
    }

    public Map<String, String> genres() {
        return genres;
    }

    public Map<String, List<String>> moods() {
        return moods;
    }

    private static StyleVocabulary load() {
        OrderedProperties properties = new OrderedProperties();
        try (InputStream in = StyleVocabulary.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Set<String> stopwords = splitList(properties.getProperty("stopwords", "")).stream()
                .collect(Collectors.toUnmodifiableSet());
        Map<String, String> genres = new LinkedHashMap<>();
        Map<String, List<String>> moods = new LinkedHashMap<>();
        for (String key : properties.orderedKeys()) {
            String value = properties.getProperty(key);
            if (key.startsWith(GENRE_PREFIX)) {
                genres.put(key.substring(GENRE_PREFIX.length()), value.trim());
            } else if (key.startsWith(MOOD_PREFIX)) {
                moods.put(key.substring(MOOD_PREFIX.length()), splitList(value));
            }
        }
        return new StyleVocabulary(stopwords, Collections.unmodifiableMap(genres), Collections.unmodifiableMap(moods));
    }

    private static List<String> splitList(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static final class OrderedProperties extends Properties {
        private final LinkedHashSet<String> order = new LinkedHashSet<>();

        @Override
        public synchronized Object put(Object key, Object value) {
            order.add((String) key);
            return super.put(key, value);
        }

        List<String> orderedKeys() {
            return List.copyOf(order);
        }
    }
}
