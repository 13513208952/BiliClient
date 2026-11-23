package com.example.piliplus;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** One process-wide thread budget for periodic work and visible-app wakes. */
final class AvailabilityLanes {
    static final int LIMIT = 4;
    private static final Executor CANCELLATIONS = Executors.newFixedThreadPool(LIMIT);

    private final Semaphore slots = new Semaphore(LIMIT);
    private final ScheduledThreadPoolExecutor control = new ScheduledThreadPoolExecutor(1,
            task -> new Thread(task, "availability-scheduler"));
    private final Map<Lease, ScheduledFuture<?>> foreground = new HashMap<>(LIMIT);
    private final LongSupplier clock;
    private final long lifetimeMs;
    private Object owner;
    private Consumer<Lease> verify;
    private boolean hangRequested;
    private volatile boolean hanging;
    private ScheduledFuture<?> sweep;
    private int wanted;

    AvailabilityLanes(LongSupplier clock, long lifetimeMs) {
        this.clock = clock;
        this.lifetimeMs = lifetimeMs;
        control.setRemoveOnCancelPolicy(true);
    }

    void wake(Object activity, BooleanSupplier enabled, Consumer<Lease> job) {
        control.execute(() -> {
            owner = activity;
            updateSweep();
            verify = job;
            if (enabled.getAsBoolean()) {
                wanted = Math.min(LIMIT, wanted + 1);
                fill();
            } else {
                disable();
            }
        });
    }

    void hide(Object activity) {
        control.execute(() -> {
            if (owner != activity) return;
            owner = null;
            updateSweep();
            verify = null;
            for (Lease lease : foreground.keySet()) lease.cancel();
        });
    }

    void enabledChanged(boolean enabled) {
        control.execute(() -> {
            if (!enabled) {
                disable();
            } else if (owner != null) {
                wanted = Math.max(1, wanted);
                fill();
            }
        });
    }

    void setHangMode(boolean enabled) {
        control.execute(() -> {
            hangRequested = enabled;
            updateSweep();
            fill();
        });
    }

    boolean isHangMode() {
        return hanging;
    }

    private void updateSweep() {
        hanging = hangRequested && owner != null;
        if (hanging && sweep == null) {
            sweep = control.scheduleWithFixedDelay(this::fill, 20, 20, TimeUnit.SECONDS);
        } else if (!hanging && sweep != null) {
            sweep.cancel(false);
            sweep = null;
        }
    }

    boolean tryReserve() {
        return slots.tryAcquire();
    }

    void release() {
        slots.release();
        control.execute(this::fill);
    }

    private void disable() {
        wanted = 0;
        for (Lease lease : foreground.keySet()) lease.cancel();
    }

    private void fill() {
        if (verify == null || wanted == 0) return;
        int limit = hanging ? LIMIT : wanted;
        int live = 0;
        for (Lease lease : foreground.keySet()) {
            if (!lease.live() || live == limit) lease.cancel();
            else live++;
        }
        while (foreground.size() < limit && tryReserve()) {
            Lease lease = new Lease(clock, lifetimeMs);
            Consumer<Lease> job = verify;
            foreground.put(lease, control.schedule(lease::cancel, lifetimeMs, TimeUnit.MILLISECONDS));
            new Thread(() -> {
                lease.bindThread();
                try {
                    if (lease.live()) job.accept(lease);
                } finally {
                    // A cancelled thread occupies its slot until it really exits.
                    slots.release();
                    control.execute(() -> {
                        foreground.remove(lease).cancel(false);
                        fill();
                    });
                }
            }, "availability-verifier").start();
        }
    }

    static final class Lease {
        private final LongSupplier clock;
        private final long deadline;
        private volatile boolean cancelled;
        private Thread thread;
        private HttpURLConnection request;

        Lease(LongSupplier clock, long lifetimeMs) {
            this.clock = clock;
            deadline = clock.getAsLong() + lifetimeMs;
        }

        boolean live() {
            return !cancelled && clock.getAsLong() < deadline;
        }

        synchronized void bindThread() {
            thread = Thread.currentThread();
        }

        synchronized boolean attach(HttpURLConnection connection) {
            if (!live()) return false;
            request = connection;
            return true;
        }

        synchronized boolean detach(HttpURLConnection connection) {
            if (request != connection) return false;
            request = null;
            return true;
        }

        void cancel() {
            HttpURLConnection connection;
            synchronized (this) {
                if (cancelled) return;
                cancelled = true;
                if (thread != null) thread.interrupt();
                connection = request;
                request = null;
            }
            if (connection != null) CANCELLATIONS.execute(connection::disconnect);
        }
    }
}
