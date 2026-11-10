package com.mixtape.infrastructure.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpStarvationTest {
    HttpServer server;
    String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            byte[] out = "ok".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() { server.stop(0); }

    private int callsFromCommonPool(RestClient client, int n, long seconds) throws Exception {
        List<CompletableFuture<String>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            fs.add(CompletableFuture.supplyAsync(() -> client.get().uri("/").retrieve().body(String.class)));
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        int done = 0;
        for (CompletableFuture<String> f : fs) {
            try {
                long left = Math.max(1, deadline - System.nanoTime());
                if ("ok".equals(f.get(left, TimeUnit.NANOSECONDS))) {
                    done++;
                }
            } catch (TimeoutException e) {
            }
        }
        return done;
    }

    @Test
    void clientBuiltByHttpFactoryDoesNotDependOnTheCommonPool() throws Exception {
        int n = ForkJoinPool.commonPool().getParallelism() * 4 + 8;
        int done = callsFromCommonPool(Http.client(base), n, 20);
        assertEquals(n, done, "all calls should finish even with the common pool saturated");
    }

    @Test
    void anUnresponsiveServerFailsFastInsteadOfHangingForever() throws Exception {
        HttpServer mute = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mute.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        }));
        mute.createContext("/", exchange -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        mute.start();
        try {
            String mutedBase = "http://127.0.0.1:" + mute.getAddress().getPort();
            RestClient client = Http.client(mutedBase, Duration.ofSeconds(1), Duration.ofSeconds(1));
            long t0 = System.nanoTime();
            assertThrows(RuntimeException.class, () -> client.get().uri("/").retrieve().body(String.class));
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertTrue(ms < 5_000, "should fail by timeout in about 1 s, took " + ms + " ms");
        } finally {
            mute.stop(0);
        }
    }
}
