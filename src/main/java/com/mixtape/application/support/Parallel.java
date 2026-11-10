package com.mixtape.application.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

// Never use a parallel stream for blocking calls: it starves the common pool the HTTP client needs.
public final class Parallel {
    static final int THREADS = 8;
    static final long TIMEOUT_SECONDS = 90;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(THREADS, runnable -> {
        Thread thread = new Thread(runnable, "io-parallel");
        thread.setDaemon(true);
        return thread;
    });

    private Parallel() {
    }

    public static <T, R> List<R> map(List<T> items, Function<T, R> function) {
        List<Callable<R>> jobs = new ArrayList<>();
        for (T item : items) {
            jobs.add(() -> function.apply(item));
        }
        List<R> results = new ArrayList<>();
        try {
            for (Future<R> future : POOL.invokeAll(jobs, TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                results.add(resultOf(future));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return results;
    }

    public static <T, R> List<R> flatMap(List<T> items, Function<T, List<R>> function) {
        List<R> results = new ArrayList<>();
        for (List<R> part : map(items, function)) {
            if (part != null) {
                results.addAll(part);
            }
        }
        return results;
    }

    private static <R> R resultOf(Future<R> future) throws InterruptedException {
        try {
            return future.get();
        } catch (CancellationException | ExecutionException e) {
            return null;
        }
    }
}
