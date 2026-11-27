package io.github.samaliyan.hush;

import android.content.Context;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds advertising messages in the message store and removes them (a copy goes to the {@link Vault}).
 * Used by every trigger: a new message arriving, a change of the store, a notification of the messaging
 * app, and "Clean up" in the app. Runs on a background thread; one pass at a time.
 */
final class Cleaner {
    /** How far back the automatic passes look (older messages are handled by "Clean up"). */
    static final long AUTO_WINDOW_MS = 3L * 24 * 3600_000L;

    static final class Found {
        final SmsStore.Msg msg;
        final Rules.Verdict verdict;

        Found(SmsStore.Msg msg, Rules.Verdict verdict) {
            this.msg = msg;
            this.verdict = verdict;
        }
    }

    static final class Result {
        int removed, denied;
        final List<String> bodies = new ArrayList<>();
        final List<String> deniedBodies = new ArrayList<>();
    }

    private static final Object LOCK = new Object();

    private Cleaner() {
    }

    /** Advertising messages received after {@code since}, newest first (nothing is removed). */
    static List<Found> find(Context c, long since, int limit) {
        SmsStore store = new SmsStore(c);
        Prefs prefs = new Prefs(c);
        Rules rules = prefs.rules();
        java.util.Set<String> restored = prefs.restored();
        Map<String, Boolean> contacts = new java.util.HashMap<>();
        List<Found> out = new ArrayList<>();
        for (SmsStore.Msg m : store.inbox(since, limit)) {
            if (!restored.isEmpty() && restored.contains(Prefs.key(m.sender, m.body))) continue;
            Rules.Verdict v = rules.check(m.sender, m.body, false);
            if (!v.remove) continue;
            // only then the (slower) contacts lookup: a contact keeps any text
            String key = m.sender == null ? "" : m.sender;
            Boolean isContact = contacts.get(key);
            if (isContact == null) {
                isContact = store.isContact(m.sender);
                contacts.put(key, isContact);
            }
            if (!isContact) out.add(new Found(m, v));
        }
        return out;
    }

    /** Automatic pass: removes the advertising messages of the last days, if removing is switched on. */
    static Result autoPass(Context c) {
        return autoPass(c, System.currentTimeMillis() - AUTO_WINDOW_MS);
    }

    /** Automatic pass over the messages received after {@code since}. */
    static Result autoPass(Context c, long since) {
        Result r = new Result();
        if (!new Prefs(c).enabled()) return r;
        synchronized (LOCK) {
            return remove(c, find(c, since, 2000));
        }
    }

    /** Removes the given messages (from "Clean up", after the user confirmed the list). */
    static Result remove(Context c, List<Found> list) {
        synchronized (LOCK) {
            Result r = new Result();
            if (list.isEmpty()) return r;
            SmsStore store = new SmsStore(c);
            Vault vault = Vault.get(c);
            Prefs prefs = new Prefs(c);
            for (Found f : list) {
                if (store.delete(f.msg.id)) {
                    vault.add(f.msg.sender, f.msg.body, f.msg.date, f.verdict.reason.label);
                    r.bodies.add(f.msg.body);
                    r.removed++;
                } else if (store.exists(f.msg.id)) {
                    r.deniedBodies.add(f.msg.body);
                    r.denied++;
                }       // else: already removed by another pass meanwhile
            }
            android.util.Log.i("Hush", "pass: removed " + r.removed + ", refused " + r.denied);
            if (r.removed > 0) {
                prefs.addRemoved(r.removed);
                prefs.setLastDenied(0);
                for (String b : r.bodies) markRemoved(b);
                for (Found f : list) markRemovedDate(f.msg.date);
                NotifListener.cancelMatching();
            }
            if (r.denied > 0 && r.removed == 0) prefs.setLastDenied(System.currentTimeMillis());
            return r;
        }
    }

    // ------------------------------------------------------------------ recently removed texts
    // The messaging app's notification for a removed message is taken away by NotifListener; it matches the
    // notification text against the texts removed (or about to be removed) in the last minutes.

    private static final long RECENT_MS = 10 * 60_000L;
    private static final Map<String, Long> RECENT = new LinkedHashMap<>();

    static void markRemoved(String body) {
        String k = Rules.normalizeText(body);
        if (k.isEmpty()) return;
        synchronized (RECENT) {
            long now = System.currentTimeMillis();
            RECENT.remove(k);
            RECENT.put(k, now);
            for (Iterator<Map.Entry<String, Long>> it = RECENT.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Long> e = it.next();
                if (now - e.getValue() > RECENT_MS || RECENT.size() > 200) it.remove();
                else break;
            }
        }
    }

    private static final List<long[]> RECENT_DATES = new ArrayList<>();     // {message date, time removed}

    /**
     * Android 15 hides the text of message notifications from other apps ("Sensitive notification content
     * hidden"), so a notification is also matched by its time: the messaging app gives it the message's date.
     */
    static void markRemovedDate(long date) {
        synchronized (RECENT_DATES) {
            long now = System.currentTimeMillis();
            RECENT_DATES.removeIf(d -> now - d[1] > RECENT_MS);
            RECENT_DATES.add(new long[]{date, now});
        }
    }

    /** A message removed in the last minutes has this date (within a second). */
    static boolean wasRemovedAt(long when) {
        if (when <= 0) return false;
        long now = System.currentTimeMillis();
        synchronized (RECENT_DATES) {
            for (long[] d : RECENT_DATES) if (now - d[1] <= RECENT_MS && Math.abs(d[0] - when) <= 1000) return true;
        }
        return false;
    }

    /** The notification text belongs to a message that was removed in the last minutes. */
    static boolean wasRemoved(CharSequence notificationText) {
        if (notificationText == null) return false;
        String t = Rules.normalizeText(notificationText.toString());
        if (t.length() < 4) return false;
        long now = System.currentTimeMillis();
        synchronized (RECENT) {
            for (Map.Entry<String, Long> e : RECENT.entrySet()) {
                if (now - e.getValue() > RECENT_MS) continue;
                if (sameText(e.getKey(), t)) return true;
            }
        }
        return false;
    }

    /** Equal, or one is the start of the other (notifications may shorten a long text). */
    static boolean sameText(String a, String b) {
        if (a.equals(b)) return true;
        String shorter = a.length() < b.length() ? a : b, longer = a.length() < b.length() ? b : a;
        if (shorter.endsWith("…")) shorter = shorter.substring(0, shorter.length() - 1).trim();
        return shorter.length() >= 12 && longer.startsWith(shorter);
    }
}
