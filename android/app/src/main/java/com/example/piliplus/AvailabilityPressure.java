package com.example.piliplus;

import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/** Admission gate shared by all verifier lanes; it never cancels in-flight requests. */
final class AvailabilityPressure {
    enum Strategy {
        CONSERVATIVE(85, 70, 256, 160),
        NORMAL(90, 78, 512, 320),
        AGGRESSIVE(0, 0, 0, 0);

        final int heapHigh, heapLow, threadsHigh, threadsLow;

        Strategy(int heapHigh, int heapLow, int threadsHigh, int threadsLow) {
            this.heapHigh = heapHigh;
            this.heapLow = heapLow;
            this.threadsHigh = threadsHigh;
            this.threadsLow = threadsLow;
        }

        boolean pause(boolean paused, long used, long max, int threads) {
            if (this == AGGRESSIVE) return false;
            if (threads < 0) return true;
            if (paused) return used > max * heapLow / 100 || threads > threadsLow;
            return used >= max * heapHigh / 100 || threads >= threadsHigh;
        }
    }

    private long nextGc;
    private static final Runtime RUNTIME = Runtime.getRuntime();
    private boolean paused;
    private long nextSample;
    private int threads = -1;

    synchronized boolean paused(Strategy strategy) {
        if (strategy == Strategy.AGGRESSIVE) {
            paused = false;
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        if (now >= nextSample) {
            nextSample = now + 1000;
            threads = readThreads();
        }
        long max = RUNTIME.maxMemory();
        long used = RUNTIME.totalMemory() - RUNTIME.freeMemory();
        paused = strategy.pause(paused, used, max, threads);
        // A quiet verifier may not allocate enough to trigger GC by itself.
        if (paused && threads >= 0 && threads <= strategy.threadsLow
                && used > max * strategy.heapLow / 100 && now >= nextGc) {
            nextGc = now + 30_000;
            RUNTIME.gc();
            paused = strategy.pause(true, RUNTIME.totalMemory() - RUNTIME.freeMemory(),
                    max, threads);
        }
        return paused;
    }

    private static int readThreads() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"), 1024)) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.startsWith("Threads:")) return Integer.parseInt(line.substring(8).trim());
            }
        } catch (IOException | NumberFormatException error) {
            Log.w("AvailabilityPressure", "Cannot sample process thread count", error);
        }
        return -1;
    }
}
