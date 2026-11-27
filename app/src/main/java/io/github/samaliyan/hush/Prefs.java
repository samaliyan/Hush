package io.github.samaliyan.hush;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Settings and small counters, in one SharedPreferences file. */
final class Prefs {
    private static final String FILE = "hush";
    private final SharedPreferences p;

    Prefs(Context c) {
        p = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ switches

    /** Removing is on (the main switch). */
    boolean enabled() { return p.getBoolean("enabled", true); }
    void setEnabled(boolean v) { p.edit().putBoolean("enabled", v).apply(); }

    /** The "advertising lines" rule (1000…, 2000…, 3000…, 5000…). */
    boolean adLines() { return p.getBoolean("ad_lines", true); }
    void setAdLines(boolean v) { p.edit().putBoolean("ad_lines", v).apply(); }

    /** Hush plays the sound for real messages (the messaging app is set to silent by the user). */
    boolean soundForReal() { return p.getBoolean("sound_real", false); }
    void setSoundForReal(boolean v) { p.edit().putBoolean("sound_real", v).apply(); }

    // ------------------------------------------------------------------ lists (one entry per line)

    List<String> allowNumbers() { return lines("allow_numbers"); }
    List<String> blockNumbers() { return lines("block_numbers"); }
    List<String> allowWords() { return lines("allow_words"); }
    List<String> blockWords() { return lines("block_words"); }

    void setAllowNumbers(List<String> v) { setLines("allow_numbers", v); }
    void setBlockNumbers(List<String> v) { setLines("block_numbers", v); }
    void setAllowWords(List<String> v) { setLines("allow_words", v); }
    void setBlockWords(List<String> v) { setLines("block_words", v); }

    void addAllowNumber(String n) {
        List<String> l = allowNumbers();
        String norm = Rules.normalizeNumber(n);
        for (String s : l) if (Rules.normalizeNumber(s).equals(norm)) return;
        l.add(n.trim());
        setAllowNumbers(l);
    }

    Rules rules() {
        return new Rules(allowNumbers(), blockNumbers(), allowWords(), blockWords(), adLines());
    }

    private List<String> lines(String key) {
        List<String> out = new ArrayList<>();
        for (String s : p.getString(key, "").split("\n")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private void setLines(String key, List<String> v) {
        StringBuilder b = new StringBuilder();
        for (String s : v) {
            String t = s.trim();
            if (t.isEmpty()) continue;
            if (b.length() > 0) b.append('\n');
            b.append(t);
        }
        p.edit().putString(key, b.toString()).apply();
    }

    // ------------------------------------------------------------------ restored messages
    // A message the user put back is never removed again (keyed by sender + text).

    static String key(String sender, String body) {
        return Rules.normalizeNumber(sender) + "|" + (sender == null ? "" : sender.trim()) + "|"
                + Integer.toHexString(Rules.normalizeText(body).hashCode());
    }

    java.util.Set<String> restored() {
        return new java.util.HashSet<>(p.getStringSet("restored", java.util.Collections.emptySet()));
    }

    synchronized void addRestored(String sender, String body) {
        java.util.Set<String> s = restored();
        s.add(key(sender, body));
        p.edit().putStringSet("restored", s).apply();
    }

    // ------------------------------------------------------------------ state

    /** Total number of messages removed since the app was installed. */
    long removedTotal() { return p.getLong("removed_total", 0); }
    synchronized void addRemoved(int n) { p.edit().putLong("removed_total", Math.max(0, removedTotal() + n)).apply(); }

    /** Time of the last message that should have been removed but Android did not allow it (0 = never). */
    long lastDenied() { return p.getLong("last_denied", 0); }
    void setLastDenied(long t) { p.edit().putLong("last_denied", t).apply(); }

    long lastUpdateCheck() { return p.getLong("last_update_check", 0); }
    void setLastUpdateCheck(long t) { p.edit().putLong("last_update_check", t).apply(); }
}
