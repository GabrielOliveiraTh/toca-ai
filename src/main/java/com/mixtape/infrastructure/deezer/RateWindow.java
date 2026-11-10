package com.mixtape.infrastructure.deezer;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.function.LongSupplier;

final class RateWindow {
    private final int max;
    private final int reserved;
    private final long windowMs;
    private final LongSupplier clock;
    private final ArrayDeque<Long> calls = new ArrayDeque<>();

    RateWindow(int max, int reserved, long windowMs, LongSupplier clock) {
        if (reserved < 0 || reserved >= max) {
            throw new IllegalArgumentException("invalid reserve");
        }
        this.max = max;
        this.reserved = reserved;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    synchronized long tryAcquire(boolean priority) {
        long now = clock.getAsLong();
        while (!calls.isEmpty() && now - calls.peekFirst() >= windowMs) {
            calls.pollFirst();
        }
        int limit = priority ? max : max - reserved;
        if (calls.size() < limit) {
            calls.addLast(now);
            return 0;
        }
        Iterator<Long> iterator = calls.iterator();
        long blockingCall = now;
        for (int index = 0; index <= calls.size() - limit && iterator.hasNext(); index++) {
            blockingCall = iterator.next();
        }
        return Math.max(1, windowMs - (now - blockingCall) + 1);
    }
}
