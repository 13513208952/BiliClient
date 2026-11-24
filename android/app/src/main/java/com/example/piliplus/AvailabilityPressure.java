package com.example.piliplus;

import android.os.SystemClock;

/** Admission gate shared by all verifier lanes; it never cancels in-flight requests. */
final class AvailabilityPressure {
    enum Strategy {
        COMPATIBLE,
        NORMAL,
        AGGRESSIVE
    }

    private long nextGc;
    private static final Runtime RUNTIME = Runtime.getRuntime();
    private boolean paused;

    synchronized boolean paused(Strategy strategy) {
        if (strategy != Strategy.NORMAL) {
            paused = false;
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        long max = RUNTIME.maxMemory();
        long used = RUNTIME.totalMemory() - RUNTIME.freeMemory();
        paused = exceedsHeapLimit(paused, used, max);
        // A quiet verifier may not allocate enough to trigger GC by itself.
        if (paused && now >= nextGc) {
            nextGc = now + 30_000;
            RUNTIME.gc();
            paused = exceedsHeapLimit(true, RUNTIME.totalMemory() - RUNTIME.freeMemory(), max);
        }
        return paused;
    }

    static boolean exceedsHeapLimit(boolean paused, long used, long max) {
        return paused ? used > max * 78 / 100 : used >= max * 90 / 100;
    }
}
