package com.mixtape.application;

import com.mixtape.application.support.Parallel;
import com.mixtape.application.view.SortResult;
import com.mixtape.domain.MoodSorter;
import com.mixtape.domain.Playlist;
import com.mixtape.domain.PlaylistRepository;
import com.mixtape.domain.PlaylistTrack;
import com.mixtape.domain.Track;
import com.mixtape.domain.error.BusinessRuleException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MoodSortService {
    private static final int MIN_TRACKS = 2;

    private final PlaylistRepository repository;
    private final BpmService bpmService;
    private final PlaylistViewAssembler assembler;

    public MoodSortService(PlaylistRepository repository, BpmService bpmService, PlaylistViewAssembler assembler) {
        this.repository = repository;
        this.bpmService = bpmService;
        this.assembler = assembler;
    }

    @Transactional
    public SortResult sortByMood(long id, String mode) {
        MoodSorter.Mode sortMode = MoodSorter.Mode.parse(mode);
        Playlist playlist = repository.require(id);
        List<Track> tracks = playlist.getTracks().stream().map(PlaylistTrack::toTrack).toList();
        if (tracks.size() < MIN_TRACKS) {
            throw new BusinessRuleException("Add at least 2 tracks to sort.");
        }

        // A dedicated pool, never a parallel stream: see Http about the deadlock with the common pool.
        Map<Long, Double> bpm = new ConcurrentHashMap<>();
        Parallel.map(tracks, track -> {
            bpmService.bpm(track.deezerId()).ifPresent(value -> bpm.put(track.deezerId(), value));
            return null;
        });
        if (bpm.size() < MIN_TRACKS) {
            throw new BusinessRuleException(
                    "Could not find the tempo (BPM) of these tracks: Deezer does not provide it and the analysis "
                            + "of the 30 s preview found no clear beat (or the previews could not be downloaded). "
                            + "Try again in a moment.");
        }

        List<Track> ordered = MoodSorter.sort(tracks, bpm, sortMode);
        Map<Long, PlaylistTrack> byId = new HashMap<>();
        playlist.getTracks().forEach(entity -> byId.put(entity.getDeezerId(), entity));
        for (int position = 0; position < ordered.size(); position++) {
            byId.get(ordered.get(position).deezerId()).setPosition(position);
        }
        playlist.sortTracksByPosition();
        int estimated = (int) bpm.keySet().stream().filter(bpmService::isEstimated).count();
        return new SortResult(assembler.toView(playlist), tracks.size() - bpm.size(), new HashMap<>(bpm), estimated);
    }
}
