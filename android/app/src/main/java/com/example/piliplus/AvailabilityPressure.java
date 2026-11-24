package com.example.piliplus;

import android.os.SystemClock;

/** Admission gate shared by all verifier lanes; it never cancels in-flight requests. */
final class AvailabilityPressure {
    enum Strategy {
        COMPATIBLE(85, 70),
        NORMAL(90, 78),
        AGGRESSIVE(0, 0);

        final int heapHigh, heapLow;

        Strategy(int heapHigh, int heapLow) {
            this.heapHigh = heapHigh;
            this.heapLow = heapLow;
        }

        boolean pause(boolean paused, long used, long max) {
            if (this == AGGRESSIVE) return false;
            if (paused) return used > max * heapLow / 100;
            return used >= max * heapHigh / 100;
        }
    }

    private long nextGc;
    private static final Runtime RUNTIME = Runtime.getRuntime();
    private boolean paused;

    synchronized boolean paused(Strategy strategy) {
        if (strategy == Strategy.AGGRESSIVE) {
            paused = false;
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        long max = RUNTIME.maxMemory();
        long used = RUNTIME.totalMemory() - RUNTIME.freeMemory();
        paused = strategy.pause(paused, used, max);
        // A quiet verifier may not allocate enough to trigger GC by itself.
        if (paused && now >= nextGc) {
            nextGc = now + 30_000;
            RUNTIME.gc();
            paused = strategy.pause(true, RUNTIME.totalMemory() - RUNTIME.freeMemory(), max);
        }
        return paused;
    }
}
