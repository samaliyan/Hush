package io.github.samaliyan.hush;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Copies of the removed messages, kept for {@link #KEEP_DAYS} days so a wrong removal can be undone.
 * Stored only inside the app (no internet permission is used for them).
 */
final class Vault extends SQLiteOpenHelper {
    static final int KEEP_DAYS = 7;
    private static final long KEEP_MS = KEEP_DAYS * 24L * 3600_000L;

    static final class Item {
        long id;
        String sender, body, reason;
        long date, removedAt;
    }

    private static Vault instance;

    static synchronized Vault get(Context c) {
        if (instance == null) instance = new Vault(c.getApplicationContext());
        return instance;
    }

    private Vault(Context c) {
        super(c, "vault.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE removed (_id INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT NOT NULL, body TEXT NOT NULL,"
                + " date INTEGER NOT NULL, reason TEXT NOT NULL, removed_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX removed_at ON removed(removed_at)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // version 1 only
    }

    void add(String sender, String body, long date, String reason) {
        ContentValues v = new ContentValues();
        v.put("sender", sender == null ? "" : sender);
        v.put("body", body == null ? "" : body);
        v.put("date", date);
        v.put("reason", reason);
        v.put("removed_at", System.currentTimeMillis());
        getWritableDatabase().insert("removed", null, v);
    }

    /** Newest first; entries older than the keep time are dropped first. */
    List<Item> list() {
        purge();
        List<Item> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("removed", null, null, null, null, null, "removed_at DESC, _id DESC", "1000")) {
            while (c.moveToNext()) {
                Item i = new Item();
                i.id = c.getLong(0);
                i.sender = c.getString(1);
                i.body = c.getString(2);
                i.date = c.getLong(3);
                i.reason = c.getString(4);
                i.removedAt = c.getLong(5);
                out.add(i);
            }
        }
        return out;
    }

    int countSince(long t) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM removed WHERE removed_at >= ?", new String[]{Long.toString(t)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    void delete(long id) {
        getWritableDatabase().delete("removed", "_id = ?", new String[]{Long.toString(id)});
    }

    void clear() {
        getWritableDatabase().delete("removed", null, null);
    }

    void purge() {
        getWritableDatabase().delete("removed", "removed_at < ?", new String[]{Long.toString(System.currentTimeMillis() - KEEP_MS)});
    }
}
