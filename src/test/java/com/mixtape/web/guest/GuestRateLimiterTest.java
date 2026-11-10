package com.mixtape.web.guest;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GuestRateLimiterTest {
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final GuestRateLimiter limiter = new GuestRateLimiter(now::get);

    @Test
    void allowsThirtyWritesPerMinuteThenBlocks() {
        for (int i = 0; i < 30; i++) {
            limiter.checkWrite("ana");
        }
        assertThrows(RateLimitException.class, () -> limiter.checkWrite("ana"));
    }

    @Test
    void windowSlidesSoActionsComeBackAfterAMinute() {
        for (int i = 0; i < 30; i++) { limiter.checkWrite("ana"); now.addAndGet(1_000); }
        assertThrows(RateLimitException.class, () -> limiter.checkWrite("ana"));
        now.addAndGet(31_000);
        assertDoesNotThrow(() -> limiter.checkWrite("ana"));
        assertThrows(RateLimitException.class, () -> {
            limiter.checkWrite("ana"); limiter.checkWrite("ana"); limiter.checkWrite("ana");
        });
    }

    @Test
    void guestsAreIndependent() {
        for (int i = 0; i < 30; i++) {
            limiter.checkWrite("ana");
        }
        assertDoesNotThrow(() -> limiter.checkWrite("bia"));
    }

    @Test
    void readsAllowSixtyAndAreSeparateFromWrites() {
        for (int i = 0; i < 60; i++) {
            limiter.checkRead("ana");
        }
        assertThrows(RateLimitException.class, () -> limiter.checkRead("ana"));
        assertDoesNotThrow(() -> limiter.checkWrite("ana"));
    }

    @Test
    void idleGuestsAreForgottenSoMemoryDoesNotGrow() {
        for (int i = 0; i < 200; i++) {
            limiter.checkRead("guest-" + i);
        }
        assertEquals(200, limiter.trackedKeys());
        now.addAndGet(5 * 60_000);
        limiter.checkRead("quem-chegou-agora");
        assertEquals(1, limiter.trackedKeys());
    }

    @Test
    void rejectionMessageIsFriendly() {
        for (int i = 0; i < 30; i++) {
            limiter.checkWrite("ana");
        }
        var e = assertThrows(RateLimitException.class, () -> limiter.checkWrite("ana"));
        assertEquals("Too many actions in a row; wait a moment.", e.getMessage());
    }
}
