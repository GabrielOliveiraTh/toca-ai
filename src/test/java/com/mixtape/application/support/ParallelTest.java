package com.mixtape.application.support;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelTest {
    @Test
    void keepsOrderAndRunsOnItsOwnPoolNotTheCommonOne() {
        var threads = ConcurrentHashMap.<String>newKeySet();
        List<Integer> in = IntStream.range(0, 20).boxed().toList();
        List<Integer> out = Parallel.map(in, i -> { threads.add(Thread.currentThread().getName()); return i * 2; });
        assertEquals(in.stream().map(i -> i * 2).toList(), out);
        assertTrue(threads.stream().allMatch(n -> n.startsWith("io-parallel")), "usou outro pool: " + threads);
        assertFalse(Thread.currentThread() instanceof ForkJoinWorkerThread);
    }

    @Test
    void runsConcurrentlyUpToThePoolSize() throws Exception {
        AtomicInteger now = new AtomicInteger(), peak = new AtomicInteger();
        List<Integer> in = IntStream.range(0, 24).boxed().toList();
        Parallel.map(in, i -> {
            int c = now.incrementAndGet();
            peak.accumulateAndGet(c, Math::max);
            try { Thread.sleep(50); } catch (InterruptedException ignored) { }
            now.decrementAndGet();
            return i;
        });
        assertTrue(peak.get() > 1, "nada rodou em paralelo");
        assertTrue(peak.get() <= Parallel.THREADS, "went over the pool limit: " + peak.get());
    }

    @Test
    void aFailingTaskBecomesNullWithoutBreakingTheOthers() {
        List<Integer> out = Parallel.map(List.of(1, 2, 3, 4), number -> {
            if (number == 3) {
                throw new IllegalStateException("x");
            }
            return number;
        });
        assertEquals(Arrays.asList(1, 2, null, 4), out);
    }

    @Test
    void flatMapJoinsPartsInItemOrderAndSkipsFailures() {
        List<String> out = Parallel.flatMap(List.of("a", "b", "c"), s -> {
            if (s.equals("b")) {
                throw new IllegalStateException("x");
            }
            return List.of(s + "1", s + "2");
        });
        assertEquals(List.of("a1", "a2", "c1", "c2"), out);
    }

    @Test
    void worksEvenWhenTheCommonPoolIsCompletelyBusy() throws Exception {
        int busy = ForkJoinPool.commonPool().getParallelism() + 4;
        for (int i = 0; i < busy; i++) {
            CompletableFuture.runAsync(() -> { try { Thread.sleep(3_000); } catch (InterruptedException ignored) { } });
        }
        long t0 = System.nanoTime();
        List<Integer> out = CompletableFuture.supplyAsync(() -> Parallel.map(List.of(1, 2, 3), i -> i + 1),
                Executors.newSingleThreadExecutor()).get(2, TimeUnit.SECONDS);
        assertEquals(List.of(2, 3, 4), out);
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 2_000);
    }
}
