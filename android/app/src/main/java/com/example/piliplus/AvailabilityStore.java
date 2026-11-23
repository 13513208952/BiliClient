package com.example.piliplus;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.util.ArrayMap;
import android.util.JsonWriter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AvailabilityStore extends SQLiteOpenHelper {
    private static final Pattern AV_LINE = Pattern.compile("av([1-9][0-9]*)");
    private static final String ENABLED = "enabled";
    private static final String RISK_UNTIL = "risk_until_ms";
    private static final String HANG_RISK_UNTIL = "hang_risk_until_ms";
    private static final String RISK_FAILURES = "risk_failures";
    private static AvailabilityStore instance;

    static synchronized AvailabilityStore get(Context context) {
        if (instance == null) {
            instance = new AvailabilityStore(context.getApplicationContext());
        }
        return instance;
    }

    private final ContentResolver resolver;
    private final SharedPreferences prefs;
    // A claim remains pending on disk until checkpointed; it survives process death.
    private final long[] claimed = new long[AvailabilityLanes.LIMIT];

    private AvailabilityStore(Context context) {
        super(context, "availability-support.sqlite", null, 1);
        setWriteAheadLoggingEnabled(true);
        resolver = context.getContentResolver();
        prefs = context.getSharedPreferences("availability-support", Context.MODE_PRIVATE);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE pending (aid INTEGER PRIMARY KEY, "
                + "retry_at_ms INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX pending_due ON pending(retry_at_ms)");
        db.execSQL("CREATE TABLE results (aid INTEGER NOT NULL UNIQUE, state TEXT NOT NULL, "
                + "code INTEGER NOT NULL, message TEXT NOT NULL, fetched_at_utc TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported availability database upgrade");
    }

    boolean isEnabled() {
        return prefs.getBoolean(ENABLED, false);
    }

    void setEnabled(boolean enabled) throws IOException {
        if (!prefs.edit().putBoolean(ENABLED, enabled).commit()) {
            throw new IOException("无法保存后台验证开关");
        }
    }

    boolean isCoolingDown(boolean hanging) {
        long ordinaryUntil = prefs.getLong(RISK_UNTIL, 0);
        long until = hanging ? prefs.getLong(HANG_RISK_UNTIL, ordinaryUntil) : ordinaryUntil;
        return until > System.currentTimeMillis();
    }

    synchronized void recordRisk(long retryAfterMs, boolean hanging) throws IOException {
        long now = System.currentTimeMillis();
        long hangUntil = prefs.getLong(HANG_RISK_UNTIL, 0);
        long ordinaryUntil = prefs.getLong(RISK_UNTIL, 0);
        int failures = prefs.getInt(RISK_FAILURES, 0);
        // Concurrent responses under one cooldown are one episode.
        if ((hanging ? hangUntil : ordinaryUntil) <= now) {
            failures = Math.min(5, failures + 1);
            long delay = TimeUnit.MINUTES.toMillis(Math.min(120, 10 << (failures - 1)));
            ordinaryUntil = Math.max(ordinaryUntil, now + delay);
            hangUntil = now + TimeUnit.MINUTES.toMillis(5);
        }
        if (!prefs.edit().putInt(RISK_FAILURES, failures)
                .putLong(RISK_UNTIL, Math.max(ordinaryUntil, retryAfterMs))
                .putLong(HANG_RISK_UNTIL, Math.max(hangUntil, retryAfterMs)).commit()) {
            throw new IOException("Could not persist availability cooldown");
        }
    }

    synchronized void clearRisk() {
        if (prefs.contains(RISK_UNTIL) && !isCoolingDown(false)) {
            prefs.edit().remove(RISK_UNTIL).remove(HANG_RISK_UNTIL).remove(RISK_FAILURES).apply();
        }
    }

    Map<String, Object> status() {
        try (Cursor counts = getReadableDatabase().rawQuery(
                "SELECT (SELECT count(*) FROM pending), (SELECT count(*) FROM results)", null)) {
            counts.moveToFirst();
            Map<String, Object> status = new ArrayMap<>(4);
            status.put("enabled", isEnabled());
            status.put("pending", counts.getLong(0));
            status.put("results", counts.getLong(1));
            status.put("coolingDown", isCoolingDown(AvailabilityWorker.isHangMode()));
            return status;
        }
    }

    long importCandidates(Uri uri) throws IOException {
        InputStream input = resolver.openInputStream(uri);
        if (input == null) {
            throw new IOException("无法打开所选文件");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                input, StandardCharsets.UTF_8.newDecoder()))) {
            SQLiteDatabase db = getWritableDatabase();
            long added = 0;
            long number = 0;
            Matcher matcher = AV_LINE.matcher("");
            db.beginTransactionNonExclusive();
            try (SQLiteStatement insert = db.compileStatement(
                    "INSERT OR IGNORE INTO pending(aid) "
                            + "SELECT ?1 WHERE NOT EXISTS(SELECT 1 FROM results WHERE aid=?1)")) {
                for (String line; (line = reader.readLine()) != null; ) {
                    if (++number == 1 && line.startsWith("\uFEFF")) {
                        line = line.substring(1);
                    }
                    line = line.trim();
                    if (line.isEmpty() || line.equals("av0")) {
                        continue;
                    }
                    if (!matcher.reset(line).matches()) {
                        throw invalidLine(number);
                    }
                    try {
                        insert.bindLong(1, Long.parseLong(matcher.group(1)));
                    } catch (NumberFormatException malformed) {
                        throw invalidLine(number);
                    }
                    if (insert.executeInsert() != -1) {
                        added++;
                    }
                }
                db.setTransactionSuccessful();
                return added;
            } finally {
                db.endTransaction();
            }
        }
    }

    private static IOException invalidLine(long number) {
        return new IOException("第 " + number + " 行不是有效的 AV 号（例如 av123）");
    }

    synchronized long claimCandidate() {
        try (SQLiteStatement next = getReadableDatabase().compileStatement(
                "SELECT coalesce((SELECT aid FROM pending WHERE retry_at_ms <= ? "
                        + "AND aid NOT IN (?,?,?,?) ORDER BY retry_at_ms, aid LIMIT 1), 0)")) {
            next.bindLong(1, System.currentTimeMillis());
            int slot = -1;
            for (int i = 0; i < claimed.length; i++) {
                next.bindLong(i + 2, claimed[i]);
                if (claimed[i] == 0) slot = i;
            }
            if (slot < 0) throw new IllegalStateException("No verification claim slot");
            long aid = next.simpleQueryForLong();
            claimed[slot] = aid;
            return aid;
        }
    }

    synchronized void releaseCandidate(long aid) {
        for (int slot = 0; slot < claimed.length; slot++) {
            if (claimed[slot] == aid) {
                claimed[slot] = 0;
                return;
            }
        }
    }

    void deferCandidate(long aid) {
        getWritableDatabase().execSQL("UPDATE pending SET retry_at_ms=? WHERE aid=?",
                new Object[]{System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(15), aid});
    }

    void checkpoint(long aid, String state, long code, String message) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            db.execSQL("INSERT INTO results(aid,state,code,message,fetched_at_utc) VALUES(?,?,?,?,?)",
                    new Object[]{aid, state, code, message, Instant.now().toString()});
            db.execSQL("DELETE FROM pending WHERE aid=?", new Object[]{aid});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    long exportResults(Uri uri) throws IOException {
        SQLiteDatabase db = getReadableDatabase();
        // The result table is append-only; a rowid cutoff keeps each export a stable snapshot.
        long cutoff = DatabaseUtils.longForQuery(db, "SELECT coalesce(max(rowid), 0) FROM results", null);
        try (Cursor rows = db.rawQuery("SELECT aid,state,code,message,fetched_at_utc FROM results "
                + "WHERE rowid <= ? ORDER BY rowid", new String[]{Long.toString(cutoff)})) {
            OutputStream output = resolver.openOutputStream(uri, "wt");
            if (output == null) {
                throw new IOException("无法打开导出文件");
            }
            try (BufferedWriter lines = new BufferedWriter(
                    new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                long count = 0;
                while (rows.moveToNext()) {
                    // JsonWriter accepts one document and would close the shared stream.
                    JsonWriter json = new JsonWriter(lines);
                    json.beginObject()
                            .name("aid").value(rows.getLong(0))
                            .name("state").value(rows.getString(1))
                            .name("code").value(rows.getLong(2))
                            // Python str.splitlines also treats the Unicode NEL as a line break.
                            .name("message").value(rows.getString(3).replace('\u0085', '\n'))
                            .name("fetched_at_utc").value(rows.getString(4))
                            .endObject();
                    lines.write('\n');
                    count++;
                }
                return count;
            }
        }
    }
}
