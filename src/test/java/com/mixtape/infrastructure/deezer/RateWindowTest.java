package com.mixtape.infrastructure.deezer;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateWindowTest {
    private final AtomicLong clock = new AtomicLong(0);
    private final RateWindow w = new RateWindow(10, 4, 1000, clock::get);

    @Test
    void backgroundCallsCanOnlyUseTheNonReservedSlots() {
        for (int i = 0; i < 6; i++) {
            assertEquals(0, w.tryAcquire(false), "background #" + i);
        }
        assertTrue(w.tryAcquire(false) > 0, "the 7th background call must wait: only the reserved slots are left");
    }

    @Test
    void userCallsStillGetThroughWhileBackgroundIsSaturated() {
        for (int i = 0; i < 6; i++) {
            w.tryAcquire(false);
        }
        for (int i = 0; i < 4; i++) {
            assertEquals(0, w.tryAcquire(true), "user #" + i);
        }
        assertTrue(w.tryAcquire(true) > 0, "above the total even the user waits");
    }

    @Test
    void reportsHowLongUntilASlotOpensAndOpensItOnTime() {
        for (int i = 0; i < 6; i++) { w.tryAcquire(false); clock.addAndGet(100); }
        long wait = w.tryAcquire(false);
        assertEquals(401, wait);
        clock.addAndGet(wait);
        assertEquals(0, w.tryAcquire(false));
    }

    @Test
    void everythingFreesUpAfterTheWindow() {
        for (int i = 0; i < 10; i++) {
            w.tryAcquire(true);
        }
        assertTrue(w.tryAcquire(true) > 0);
        clock.addAndGet(1000);
        for (int i = 0; i < 6; i++) {
            assertEquals(0, w.tryAcquire(false));
        }
    }

    @Test
    void rejectsAnInvalidReserve() {
        assertThrows(IllegalArgumentException.class, () -> new RateWindow(5, 5, 1000, clock::get));
        assertThrows(IllegalArgumentException.class, () -> new RateWindow(5, -1, 1000, clock::get));
    }
}
