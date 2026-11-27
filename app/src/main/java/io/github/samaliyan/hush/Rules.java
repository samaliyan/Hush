package io.github.samaliyan.hush;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides whether a text message is advertising. Plain Java (no Android), so it is unit-tested on its own.
 *
 * <p>Order of the checks (the first one that matches decides):
 * <ol>
 *   <li>sender on the user's Allow list, or a saved contact: keep;</li>
 *   <li>a word from the user's Allow words: keep;</li>
 *   <li>sender on the user's Block list (a number or the start of a number): remove;</li>
 *   <li>an unsubscribe code ("لغو۱۱", "لغو 11" …). Iranian law requires it in every advertising text,
 *       and real messages (banks, codes, people) never have it: remove;</li>
 *   <li>a login/verification code or a bank transaction (balance, withdrawal …): keep;</li>
 *   <li>a word from the user's Block words: remove;</li>
 *   <li>sent from a bulk advertising line (1000…, 2000…, 3000…, 5000…), if that rule is on: remove;</li>
 *   <li>anything else: keep.</li>
 * </ol>
 */
final class Rules {

    enum Reason {
        ALLOWED_SENDER("Sender is on your Allow list"),
        CONTACT("Sender is in your contacts"),
        ALLOWED_WORD("Contains an allowed word"),
        BLOCKED_SENDER("Sender is on your Block list"),
        UNSUBSCRIBE_CODE("Has an unsubscribe code (لغو)"),
        CODE_OR_BANK("Looks like a login code or bank message"),
        BLOCKED_WORD("Contains a blocked word"),
        AD_LINE("Sent from an advertising line"),
        NONE("No rule matched");

        final String label;

        Reason(String label) {
            this.label = label;
        }
    }

    static final class Verdict {
        final boolean remove;
        final Reason reason;

        Verdict(boolean remove, Reason reason) {
            this.remove = remove;
            this.reason = reason;
        }

        @Override public String toString() {
            return (remove ? "REMOVE " : "KEEP ") + reason;
        }
    }

    /** Number starts of the bulk advertising lines in Iran. */
    static final List<String> AD_LINE_PREFIXES = Collections.unmodifiableList(java.util.Arrays.asList("1000", "2000", "3000", "5000"));

    /** "لغو" followed (after at most a few spaces or marks) by a number: the unsubscribe code. */
    private static final Pattern UNSUBSCRIBE = Pattern.compile("(لغو|انصراف)\\s*(عضویت|ارسال)?[\\s:=\\-_.،,()\\[\\]«»\"'*]{0,4}(عدد\\s*)?[0-9]{1,4}(?![0-9])");

    /** Login / verification codes, in Persian and English. */
    private static final Pattern CODE = Pattern.compile(
            "(کد|رمز)\\s*(ی|های)?\\s*(تایید|تأیید|تاييد|ورود|یکبار|یک بار|یک‌بار|پویا|فعال\\s*سازی|فعال‌سازی|عبور|امنیتی|موقت|دوم|اعتبارسنجی|احراز)"
                    + "|رمز\\s*پویا|کد\\s*شما|رمز\\s*شما"
                    + "|(?i:\\b(otp|verification|verify|one[- ]?time|passcode|password|login code|security code|your code|auth(entication)? code)\\b)");

    /** Bank transaction texts. */
    private static final Pattern BANK = Pattern.compile(
            "مانده|موجودی|برداشت\\s*[:：]|واریز\\s*[:：]|انتقال\\s*[:：]|(?i:\\b(mande|balance|bardasht|variz)\\b)");

    private final List<String> allowNumbers, allowNames;
    private final List<String> blockNumbers, blockNames;
    private final List<String> allowWords;
    private final List<String> blockWords;
    private final boolean adLines;

    /**
     * @param allowNumbers senders that are never removed: numbers (any format; compared after normalising)
     *                     or sender names ("Snapp", compared without case)
     * @param blockNumbers senders that are always removed: numbers, starts of numbers, or sender names
     * @param allowWords   words/phrases that keep a message
     * @param blockWords   words/phrases that remove a message
     * @param adLines      the "advertising lines" rule (1000…, 2000…, 3000…, 5000…) is on
     */
    Rules(List<String> allowNumbers, List<String> blockNumbers, List<String> allowWords, List<String> blockWords, boolean adLines) {
        this.allowNumbers = normNumbers(allowNumbers);
        this.blockNumbers = normNumbers(blockNumbers);
        this.allowNames = names(allowNumbers);
        this.blockNames = names(blockNumbers);
        this.allowWords = normWords(allowWords);
        this.blockWords = normWords(blockWords);
        this.adLines = adLines;
    }

    /**
     * @param sender    the sender as Android reports it ("+98300012", "3000505", "Snapp" …)
     * @param body      the full text
     * @param inContacts the sender is a saved contact
     */
    Verdict check(String sender, String body, boolean inContacts) {
        String num = normalizeNumber(sender);
        String text = normalizeText(body);

        String name = num.isEmpty() ? normalizeText(sender) : "";
        if (!num.isEmpty() ? matchesExact(allowNumbers, num) : matchesExact(allowNames, name)) return keep(Reason.ALLOWED_SENDER);
        if (inContacts) return keep(Reason.CONTACT);
        if (containsAny(text, allowWords)) return keep(Reason.ALLOWED_WORD);
        if (!num.isEmpty() ? startsWithAny(num, blockNumbers) : matchesExact(blockNames, name)) return remove(Reason.BLOCKED_SENDER);
        if (UNSUBSCRIBE.matcher(text).find()) return remove(Reason.UNSUBSCRIBE_CODE);
        if (CODE.matcher(text).find() || BANK.matcher(text).find()) return keep(Reason.CODE_OR_BANK);
        if (containsAny(text, blockWords)) return remove(Reason.BLOCKED_WORD);
        if (adLines && isAdLine(num)) return remove(Reason.AD_LINE);
        return keep(Reason.NONE);
    }

    // ------------------------------------------------------------------ helpers (package-visible for tests)

    static boolean isAdLine(String normalizedNumber) {
        return startsWithAny(normalizedNumber, AD_LINE_PREFIXES);
    }

    /**
     * The national form of a number, digits only: "+98 912 123 4567", "00989121234567", "09121234567" and
     * "9121234567" all become "9121234567"; "+9830001234" becomes "30001234". Persian/Arabic digits are read.
     * A sender name without digits ("Snapp") gives "".
     */
    static String normalizeNumber(String raw) {
        if (raw == null) return "";
        String s = digitsToAscii(raw).trim();
        // a sender name ("Bank Mellat", "Snapp") is not a number
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) return "";
        }
        boolean plus = s.startsWith("+");
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') d.append(c);
        }
        String n = d.toString();
        if (n.startsWith("0098")) n = n.substring(4);
        else if (n.startsWith("98") && (plus || n.length() >= 11)) n = n.substring(2);
        else if (n.startsWith("0") && !n.startsWith("00")) n = n.substring(1);    // 0912… (mobile), 021… (landline), "0935" (a start)
        return n;
    }

    /** Persian (۰–۹) and Arabic (٠–٩) digits → 0–9. */
    static String digitsToAscii(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u06F0' && c <= '\u06F9') c = (char) ('0' + (c - '\u06F0'));
            else if (c >= '\u0660' && c <= '\u0669') c = (char) ('0' + (c - '\u0660'));
            b.append(c);
        }
        return b.toString();
    }

    /**
     * Text for matching: ASCII digits, Arabic ي/ك → Persian ی/ک, zero-width joiners and tatweel removed,
     * whitespace collapsed, lower case.
     */
    static String normalizeText(String s) {
        if (s == null) return "";
        String t = digitsToAscii(s)
                .replace('\u064A', '\u06CC').replace('\u0649', '\u06CC').replace('\u0643', '\u06A9')
                .replace("\u200C", " ").replace("\u200D", "").replace("\u200E", "").replace("\u200F", "")
                .replace("\u0640", "");
        return t.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> normNumbers(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in == null) return out;
        for (String s : in) {
            String n = normalizeNumber(s);
            if (!n.isEmpty()) out.add(n);
        }
        return out;
    }

    /** The entries that are sender names (have no digits to compare), normalised. */
    private static List<String> names(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in == null) return out;
        for (String s : in) {
            if (normalizeNumber(s).isEmpty()) {
                String n = normalizeText(s);
                if (!n.isEmpty()) out.add(n);
            }
        }
        return out;
    }

    private static List<String> normWords(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in == null) return out;
        for (String s : in) {
            String w = normalizeText(s);
            if (!w.isEmpty()) out.add(w);
        }
        return out;
    }

    private static boolean matchesExact(List<String> list, String n) {
        for (String s : list) if (s.equals(n)) return true;
        return false;
    }

    private static boolean startsWithAny(String n, List<String> prefixes) {
        if (n.isEmpty()) return false;
        for (String p : prefixes) if (!p.isEmpty() && n.startsWith(p)) return true;
        return false;
    }

    private static boolean containsAny(String text, List<String> words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }

    private static Verdict keep(Reason r) {
        return new Verdict(false, r);
    }

    private static Verdict remove(Reason r) {
        return new Verdict(true, r);
    }
}
