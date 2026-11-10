package com.mixtape.application;

import com.mixtape.application.port.StyleCandidateSource;
import com.mixtape.domain.style.StyleCandidate;
import com.mixtape.domain.style.StyleIntent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Component
public class StyleWorkers {
    private static final int POOL_SIZE = 4;
    private static final long SOURCE_TIMEOUT_SECONDS = 30;
    private static final long BATCH_TIMEOUT_SECONDS = 60;

    private final ExecutorService sourcesPool = daemonPool(POOL_SIZE);
    private final ExecutorService resolvePool = daemonPool(POOL_SIZE);

    private static ExecutorService daemonPool(int size) {
        return Executors.newFixedThreadPool(size, runnable -> {
            Thread thread = new Thread(runnable, "style-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public List<StyleCandidate> gather(List<StyleCandidateSource> sources, StyleIntent intent) {
        List<CompletableFuture<List<StyleCandidate>>> futures = sources.stream()
                .map(source -> supply(() -> source.candidates(intent)))
                .toList();
        List<StyleCandidate> all = new ArrayList<>();
        for (CompletableFuture<List<StyleCandidate>> future : futures) {
            try {
                all.addAll(future.get(SOURCE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (Exception e) {
            }
        }
        return all;
    }

    private CompletableFuture<List<StyleCandidate>> supply(Callable<List<StyleCandidate>> source) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return source.call();
            } catch (Exception e) {
                return List.<StyleCandidate>of();
            }
        }, sourcesPool);
    }

    public void runAll(List<Callable<Void>> jobs) {
        try {
            resolvePool.invokeAll(jobs, BATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public <T> List<T> runJobs(List<Callable<T>> jobs) {
        List<T> results = new ArrayList<>();
        try {
            for (Future<T> future : resolvePool.invokeAll(jobs, BATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                try {
                    T value = future.get();
                    if (value != null) {
                        results.add(value);
                    }
                } catch (ExecutionException | RuntimeException e) {
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return results;
    }
}
