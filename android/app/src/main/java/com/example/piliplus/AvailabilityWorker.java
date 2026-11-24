package com.example.piliplus;

import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.SystemClock;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Keep // WorkManager persists the worker's class name and constructs it by reflection.
public final class AvailabilityWorker extends Worker {
    private static final String WORK_NAME = "availability-support";
    private static final String VIEW_API = "https://api.bilibili.com/x/web-interface/view?aid=";
    // A regular WorkManager job is subject to a shorter system window.
    private static final long BACKGROUND_MS = TimeUnit.MINUTES.toMillis(7);
    private static final long FOREGROUND_MS = TimeUnit.MINUTES.toMillis(15);
    private static final long IDLE_RETRY_MS = 20_000;
    private static final long REQUEST_MS = 30_000;
    private static final int MAX_API_BYTES = 1_048_576;
    private static final long INVALID_INTEGER = Long.MIN_VALUE;
    private static final AvailabilityLanes LANES =
            new AvailabilityLanes(SystemClock::elapsedRealtime, FOREGROUND_MS);
    private static Network pooledWifi;

    private static synchronized Network pooledWifi(Network current) {
        // Android attaches its URL factory (and pool) to the Network object,
        // not its ID. Binder returns a new object for repeated lookups.
        if (current == null || !current.equals(pooledWifi)) pooledWifi = current;
        return pooledWifi;
    }

    static void setEnabled(Context context, AvailabilityStore store, boolean enabled)
            throws IOException, ExecutionException, InterruptedException {
        WorkManager manager = WorkManager.getInstance(context);
        if (!enabled) {
            store.setEnabled(false);
            LANES.enabledChanged(false);
            manager.cancelUniqueWork(WORK_NAME).getResult().get();
            return;
        }
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkRequest(new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                        .build(), NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                AvailabilityWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        // Persist the work before reporting the setting as enabled. The worker shares this
        // monitor so its first invocation cannot see the previous setting during the change.
        synchronized (store) {
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
                    .getResult().get();
            store.setEnabled(true);
        }
        LANES.enabledChanged(true);
    }

    private volatile AvailabilityLanes.Lease background;

    static void onWake(Context context, Object owner) {
        LANES.wake(owner, () -> AvailabilityStore.get(context).isEnabled(),
                lease -> runForeground(context, lease));
    }

    static void onHidden(Object owner) {
        LANES.hide(owner);
    }

    static void setHangMode(boolean active) {
        LANES.setHangMode(active);
    }

    private static void runForeground(Context context, AvailabilityLanes.Lease lease) {
        AvailabilityStore store = AvailabilityStore.get(context);
        while (lease.live() && store.isEnabled()) {
            run(context, lease);
            if (!lease.live()) return;
            // Idle, network loss, or cooldown; never wait after success.
            try {
                Thread.sleep(IDLE_RETRY_MS);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public AvailabilityWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (isHangMode()) return Result.success();
        if (!LANES.tryReserve()) return Result.retry();
        AvailabilityLanes.Lease lease =
                new AvailabilityLanes.Lease(SystemClock::elapsedRealtime, BACKGROUND_MS);
        background = lease;
        try {
            if (isStopped()) return Result.success();
            return run(getApplicationContext(), lease);
        } finally {
            background = null;
            LANES.release();
        }
    }

    private static Result run(Context context, AvailabilityLanes.Lease lease) {
        try {
            return runBatch(context, lease);
        } catch (IOException | SQLiteException failure) {
            Log.w("AvailabilityWorker", "Could not save availability progress", failure);
            return Result.retry();
        }
    }

    private static Result runBatch(Context context, AvailabilityLanes.Lease lease)
            throws IOException {
        AvailabilityStore store = AvailabilityStore.get(context);
        synchronized (store) {
            if (!store.isEnabled()) return Result.success();
        }
        ConnectivityManager networks = context.getSystemService(ConnectivityManager.class);
        while (lease.live() && store.isEnabled() && !store.isCoolingDown(isHangMode())) {
            Network wifi = activeWifi(networks);
            boolean reuseConnections = store.reusesConnections();
            if (reuseConnections) wifi = pooledWifi(wifi);
            if (wifi == null) return Result.retry();
            if (store.resourcePaused()) return Result.retry();
            long aid = store.claimCandidate();
            if (aid == 0) return Result.success();
            try {
                Observation observation = fetch(networks, wifi, store, aid, lease, reuseConnections);
                if (observation == null || !lease.live() || !store.isEnabled()
                        || !wifi.equals(activeWifi(networks))) {
                    return Result.retry();
                }
                store.checkpoint(aid, observation.state, observation.code, observation.message);
                store.clearRisk();
            } catch (RiskControl risk) {
                store.recordRisk(risk.retryAfterMs, isHangMode());
                return Result.success();
            } catch (IOException | IllegalStateException transientFailure) {
                if (lease.live() && store.isEnabled() && wifi.equals(activeWifi(networks))) {
                    store.deferCandidate(aid);
                }
                return Result.retry();
            } finally {
                store.releaseCandidate(aid);
            }
        }
        return Result.success();
    }

    static boolean isHangMode() {
        return LANES.isHangMode();
    }

    private static Network activeWifi(ConnectivityManager networks) {
        Network active = networks.getActiveNetwork();
        if (active == null) {
            return null;
        }
        NetworkCapabilities caps = networks.getNetworkCapabilities(active);
        return caps != null
                && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                && !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? active : null;
    }

    private static Observation fetch(ConnectivityManager networks, Network wifi,
            AvailabilityStore store, long aid, AvailabilityLanes.Lease lease,
            boolean reuseConnections) throws IOException {
        // Bind DNS and sockets to this Wi-Fi Network, with no fallback to the default route.
        HttpURLConnection http = (HttpURLConnection) wifi.openConnection(new URL(VIEW_API + aid));
        if (!lease.attach(http)) {
            http.disconnect();
            return null;
        }
        boolean consumed = false;
        try {
            http.setConnectTimeout(10_000);
            http.setReadTimeout(10_000);
            http.setInstanceFollowRedirects(false);
            http.setUseCaches(false);
            http.setRequestProperty("Accept", "application/json");
            http.setRequestProperty("Referer", "https://www.bilibili.com/");
            http.setRequestProperty("User-Agent", "BiliClient/availability");
            if (!lease.live() || !store.isEnabled() || store.isCoolingDown(isHangMode())
                    || !wifi.equals(activeWifi(networks))) {
                return null;
            }
            long deadline = SystemClock.elapsedRealtime() + REQUEST_MS;
            int status = http.getResponseCode();
            if (isRiskCode(status)) {
                throw new RiskControl(retryAfter(http.getHeaderField("Retry-After")));
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Unexpected HTTP status");
            }
            Observation observation;
            try (JsonReader json = new JsonReader(new InputStreamReader(
                    new BoundedResponse(http.getInputStream(), deadline),
                    StandardCharsets.UTF_8.newDecoder()))) {
                observation = parseObservation(json, aid, http.getHeaderField("Retry-After"));
            }
            consumed = true;
            return observation;
        } finally {
            if (lease.detach(http) && (!reuseConnections || !consumed)) http.disconnect();
        }
    }

    @Override
    public void onStopped() {
        AvailabilityLanes.Lease lease = background;
        if (lease != null) lease.cancel();
    }

    private static Observation parseObservation(JsonReader json, long aid, String retryAfterHeader)
            throws IOException {
        long code = INVALID_INTEGER;
        String message = "";
        long responseAid = 0;
        long responseState = INVALID_INTEGER;
        json.beginObject();
        while (json.hasNext()) {
            switch (json.nextName()) {
                case "code" -> code = nextInteger(json);
                case "message" -> {
                    if (json.peek() == JsonToken.STRING) {
                        message = json.nextString();
                    } else {
                        json.skipValue();
                    }
                }
                case "data" -> {
                    responseAid = 0;
                    responseState = INVALID_INTEGER;
                    if (json.peek() != JsonToken.BEGIN_OBJECT) {
                        json.skipValue();
                        break;
                    }
                    responseState = 0;
                    json.beginObject();
                    while (json.hasNext()) {
                        switch (json.nextName()) {
                            case "aid" -> responseAid = nextInteger(json);
                            case "state" -> {
                                if (json.peek() == JsonToken.NULL) {
                                    json.nextNull();
                                    responseState = 0;
                                } else {
                                    responseState = nextInteger(json);
                                }
                            }
                            default -> json.skipValue();
                        }
                    }
                    json.endObject();
                }
                default -> json.skipValue();
            }
        }
        json.endObject();
        if (json.peek() != JsonToken.END_DOCUMENT || code == INVALID_INTEGER) {
            throw new IOException("Incomplete availability reply");
        }
        if (isRiskCode(code)) {
            throw new RiskControl(retryAfter(retryAfterHeader));
        }
        if ((code >= 500 && code <= 599) || (code <= -500 && code >= -599)) {
            throw new IOException("Transient API error");
        }
        if (code == 0) {
            if (responseAid != aid || responseState == INVALID_INTEGER) {
                throw new IOException("Unverified success reply");
            }
            return new Observation(responseState == 0 ? "valid" : "unknown", code, message);
        }
        // The host importer only vetoes these two explicit API codes, never HTTP 404.
        String state = code == -404 || code == 62002 ? "unavailable" : "unknown";
        return new Observation(state, code, message);
    }

    private static long nextInteger(JsonReader json) throws IOException {
        if (json.peek() == JsonToken.NUMBER) {
            try {
                return Long.parseLong(json.nextString());
            } catch (NumberFormatException malformed) {
                return INVALID_INTEGER;
            }
        }
        json.skipValue();
        return INVALID_INTEGER;
    }

    private static boolean isRiskCode(long code) {
        return code == 412 || code == 429 || code == -412 || code == -429
                || code == -352 || code == -509;
    }

    private static long retryAfter(String header) {
        if (header == null) {
            return 0;
        }
        String value = header.trim();
        try {
            long delay = TimeUnit.SECONDS.toMillis(Long.parseLong(value));
            long now = System.currentTimeMillis();
            return delay <= 0 ? 0 : now + Math.min(delay, Long.MAX_VALUE - now);
        } catch (NumberFormatException notSeconds) {
            try {
                return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli();
            } catch (DateTimeParseException | ArithmeticException invalid) {
                return 0;
            }
        }
    }

    private static final class Observation {
        final String state;
        final long code;
        final String message;

        Observation(String state, long code, String message) {
            this.state = state;
            this.code = code;
            this.message = message;
        }
    }

    private static final class RiskControl extends IOException {
        final long retryAfterMs;

        RiskControl(long retryAfterMs) {
            this.retryAfterMs = retryAfterMs;
        }
    }

    private static final class BoundedResponse extends FilterInputStream {
        private final long deadline;
        private int consumed;

        BoundedResponse(InputStream in, long deadline) {
            super(in);
            this.deadline = deadline;
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            checkBounds(value == -1 ? 0 : 1);
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = in.read(bytes, offset, length);
            checkBounds(Math.max(count, 0));
            return count;
        }

        private void checkBounds(int bytesRead) throws IOException {
            consumed += bytesRead;
            if (consumed > MAX_API_BYTES || SystemClock.elapsedRealtime() > deadline) {
                throw new IOException("Availability response limit exceeded");
            }
        }
    }
}
