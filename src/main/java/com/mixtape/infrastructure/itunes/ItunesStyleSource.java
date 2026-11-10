package com.mixtape.infrastructure.itunes;

import com.mixtape.application.port.StyleCandidateSource;
import com.mixtape.domain.style.StyleCandidate;
import com.mixtape.domain.style.StyleIntent;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(2)
public class ItunesStyleSource implements StyleCandidateSource {
    private static final int GENRE_TERMS = 3;
    private static final int RESULTS_PER_TERM = 200;

    private final ItunesClient client;

    ItunesStyleSource(ItunesClient client) {
        this.client = client;
    }

    @Override
    public String key() {
        return "itunes";
    }

    @Override
    public String displayName() {
        return "iTunes";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<StyleCandidate> candidates(StyleIntent intent) {
        List<String> terms = new ArrayList<>(intent.genres().stream().limit(GENRE_TERMS).toList());
        if (terms.isEmpty() && !intent.terms().isEmpty()) {
            terms.add(String.join(" ", intent.terms()));
        }
        if (!intent.raw().isBlank() && !terms.contains(intent.raw())) {
            terms.add(intent.raw());
        }
        List<StyleCandidate> out = new ArrayList<>();
        for (String term : terms) {
            for (ItunesClient.Hit hit : client.search(term, RESULTS_PER_TERM)) {
                out.add(new StyleCandidate(hit.artist(), hit.title(), hit.year(), hit.genre(), null, null, null,
                        key(), false));
            }
        }
        return out;
    }
}
