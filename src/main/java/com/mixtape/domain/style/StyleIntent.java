package com.mixtape.domain.style;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record StyleIntent(String raw, Integer yearFrom, Integer yearTo, List<String> genres, List<String> terms,
                          boolean englishPeriod) {
    private static final Pattern DECADE = Pattern.compile(
            "(?i)\\banos?\\s*(\\d{2,4})\\b|\\bd[eé]cada\\s+de\\s+(\\d{2,4})\\b");
    private static final Pattern ENGLISH_DECADE = Pattern.compile("(?i)(?<![\\w])['’]?(\\d{4}|\\d{2})s\\b");
    private static final Pattern YEAR = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");
    private static final Pattern PERIOD_TOKEN = Pattern.compile("['’]?\\d+s?");

    public boolean hasYears() {
        return yearFrom != null;
    }

    public String decadeLabel() {
        boolean wholeDecade = yearFrom != null && yearTo != null && yearTo - yearFrom == 9 && yearFrom % 10 == 0;
        if (!wholeDecade) {
            return null;
        }
        int shown = yearFrom < 2000 ? yearFrom - 1900 : yearFrom;
        return englishPeriod ? shown + "s" : "anos " + shown;
    }

    public boolean matchesPeriod(String title) {
        if (!hasYears() || title == null) {
            return false;
        }
        String text = " " + strip(title).replaceAll("[^a-z0-9]+", " ") + " ";
        for (int year = yearFrom; year <= yearTo; year++) {
            if (text.contains(" " + year + " ")) {
                return true;
            }
        }
        if (decadeLabel() == null) {
            return false;
        }
        int decade = yearFrom;
        String shortForm = String.format("%02d", decade % 100);
        return text.contains(" anos " + shortForm + " ") || text.contains(" " + shortForm + "s ")
                || text.contains(" anos " + decade + " ") || text.contains(" " + decade + "s ")
                || text.contains(" decada de " + shortForm + " ") || text.contains(" decada de " + decade + " ");
    }

    public static String strip(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    public static StyleIntent parse(String raw) {
        StyleVocabulary vocabulary = StyleVocabulary.get();
        String text = raw == null ? "" : raw.trim().replaceAll("\\s{2,}", " ");
        String plain = strip(text);

        Integer from = null;
        Integer to = null;
        boolean english = false;
        Matcher decade = DECADE.matcher(text);
        Matcher englishDecade = ENGLISH_DECADE.matcher(text);
        Matcher year = YEAR.matcher(text);
        if (decade.find()) {
            int number = Integer.parseInt(decade.group(1) != null ? decade.group(1) : decade.group(2));
            from = decadeStart(number);
            to = decadeEnd(number, from);
        } else if (englishDecade.find()) {
            int number = Integer.parseInt(englishDecade.group(1));
            from = decadeStart(number);
            to = decadeEnd(number, from);
            english = true;
        } else if (year.find()) {
            from = Integer.parseInt(year.group(1));
            to = from;
        }

        List<String> genres = new ArrayList<>();
        for (Map.Entry<String, String> entry : vocabulary.genres().entrySet()) {
            Pattern alias = Pattern.compile("(^|[^a-z0-9])" + Pattern.quote(entry.getKey()) + "([^a-z0-9]|$)");
            if (alias.matcher(plain).find() && !genres.contains(entry.getValue())) {
                genres.add(entry.getValue());
            }
        }

        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}&]+")) {
            String stripped = strip(word);
            boolean skip = word.isBlank() || vocabulary.stopwords().contains(stripped)
                    || PERIOD_TOKEN.matcher(stripped).matches();
            if (!skip) {
                terms.add(word);
            }
        }
        LinkedHashSet<String> moodTerms = new LinkedHashSet<>();
        for (String word : plain.split("[^a-z0-9]+")) {
            List<String> mood = vocabulary.moods().get(word);
            if (mood != null) {
                moodTerms.addAll(mood);
            }
        }
        terms.addAll(moodTerms);
        return new StyleIntent(text, from, to, List.copyOf(genres), List.copyOf(terms), english);
    }

    private static int decadeStart(int number) {
        if (number >= 1000) {
            return number;
        }
        return number >= 30 ? 1900 + number : 2000 + number;
    }

    private static int decadeEnd(int number, int start) {
        boolean singleYear = number >= 1000 && number % 10 != 0;
        return singleYear ? start : start + 9;
    }

    public List<String> playlistQueries() {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        if (!raw.isBlank()) {
            queries.add(raw);
        }
        String label = decadeLabel();
        for (String genre : genres) {
            if (label != null) {
                queries.add(genre + " " + label);
            }
            for (String term : terms) {
                if (!term.equalsIgnoreCase(genre) && term.length() > 2) {
                    queries.add(genre + " " + term);
                }
            }
        }
        if (!terms.isEmpty()) {
            queries.add(String.join(" ", terms));
        }
        if (label != null) {
            for (String term : terms) {
                if (!genres.contains(strip(term))) {
                    queries.add(term + " " + label);
                }
            }
        }
        return queries.stream().limit(6).toList();
    }
}
