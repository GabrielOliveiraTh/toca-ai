package com.mixtape.web.guest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Component
public class GuestRateLimiter {
    public static final int WRITES_PER_MINUTE = 30;
    public static final int READS_PER_MINUTE = 60;

    private static final long WINDOW_MS = 60_000;

    private final LongSupplier clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private long lastCleanup;

    @Autowired
    public GuestRateLimiter() {
        this(System::currentTimeMillis);
    }

    GuestRateLimiter(LongSupplier clock) {
        this.clock = clock;
        this.lastCleanup = clock.getAsLong();
    }

    public void checkWrite(String guestId) {
        check("w:" + guestId, WRITES_PER_MINUTE);
    }

    public void checkRead(String guestId) {
        check("r:" + guestId, READS_PER_MINUTE);
    }

    private void check(String key, int limit) {
        long now = clock.getAsLong();
        cleanup(now);
        Deque<Long> times = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MS) {
                times.pollFirst();
            }
            if (times.size() >= limit) {
                throw new RateLimitException("Too many actions in a row; wait a moment.");
            }
            times.addLast(now);
        }
    }

    private synchronized void cleanup(long now) {
        if (now - lastCleanup < WINDOW_MS) {
            return;
        }
        lastCleanup = now;
        hits.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                Long last = entry.getValue().peekLast();
                return last == null || now - last >= WINDOW_MS;
            }
        });
    }

    int trackedKeys() {
        return hits.size();
    }
}
