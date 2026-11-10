package com.mixtape.application;

import com.mixtape.application.port.TempoEstimator;
import com.mixtape.application.support.Parallel;
import com.mixtape.infrastructure.http.Http;
import com.mixtape.support.FakeTrackMetadata;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BpmServiceTest {
    static class FakeDeezer extends FakeTrackMetadata {
        final AtomicInteger calls = new AtomicInteger();
        final RestClient http;
        FakeDeezer(String base) { this.http = Http.client(base); }
        @Override public Optional<OptionalDouble> bpm(long id) {
            calls.incrementAndGet();
            http.get().uri("/{id}", id).retrieve().body(String.class);
            if (id % 10 == 0) {
                return Optional.empty();
            }
            if (id % 10 == 5) {
                return Optional.of(OptionalDouble.empty());
            }
            return Optional.of(OptionalDouble.of(100 + id));
        }
    }

    private static final TempoEstimator NO_ESTIMATE = id -> Optional.of(OptionalDouble.empty());

    HttpServer server;
    String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.createContext("/", ex -> {
            try { Thread.sleep(40); } catch (InterruptedException ignored) { }
            byte[] out = "{}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void manyParallelLookupsWithRealNetworkFinishInsteadOfDeadlocking() throws Exception {
        FakeDeezer deezer = new FakeDeezer(base);
        BpmService bpm = new BpmService(deezer, NO_ESTIMATE);
        List<Long> ids = IntStream.rangeClosed(1, 40).mapToObj(Long::valueOf).toList();

        long t0 = System.nanoTime();
        CompletableFuture<List<Optional<Double>>> run = CompletableFuture.supplyAsync(() -> Parallel.map(ids, bpm::bpm));
        List<Optional<Double>> result = run.get(20, TimeUnit.SECONDS);
        long ms = (System.nanoTime() - t0) / 1000;

        assertEquals(40, result.size());
        assertEquals(40, deezer.calls.get());
        assertTrue(result.get(0).isPresent());
        assertEquals(101.0, result.get(0).get());
        assertTrue(result.get(4).isEmpty());
        assertTrue(ms >= 0);
    }

    @Test
    void lookupsRunInParallelNotOneByOne() throws Exception {
        BpmService bpm = new BpmService(new FakeDeezer(base), NO_ESTIMATE);
        List<Long> ids = IntStream.rangeClosed(1, 32).mapToObj(Long::valueOf).toList();
        long t0 = System.nanoTime();
        CompletableFuture.supplyAsync(() -> Parallel.map(ids, bpm::bpm)).get(20, TimeUnit.SECONDS);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 1_000, "deveria ser paralelo, levou " + ms + " ms");
    }

    @Test
    void conclusiveAnswersAreCachedButFailuresAreNot() {
        FakeDeezer deezer = new FakeDeezer(base);
        BpmService bpm = new BpmService(deezer, NO_ESTIMATE);

        assertEquals(Optional.of(103.0), bpm.bpm(3));
        assertEquals(Optional.of(103.0), bpm.bpm(3));
        assertEquals(1, deezer.calls.get());

        assertTrue(bpm.bpm(15).isEmpty());
        assertTrue(bpm.bpm(15).isEmpty());
        assertEquals(2, deezer.calls.get());

        assertTrue(bpm.bpm(20).isEmpty());
        assertTrue(bpm.bpm(20).isEmpty());
        assertEquals(4, deezer.calls.get());
    }
}
