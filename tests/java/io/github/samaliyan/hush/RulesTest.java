package io.github.samaliyan.hush;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Plain-Java tests of Rules (run by tests/run_core.sh). Exits with 1 on the first failure summary. */
public final class RulesTest {
    private static int fails, passes;

    private static final List<String> NONE = Collections.emptyList();
    private static final Rules DEFAULT = new Rules(NONE, NONE, NONE, NONE, true);

    public static void main(String[] a) {
        numbers();
        ads();
        keeps();
        userLists();
        adLinesOff();
        System.out.println("RulesTest: " + passes + " passed, " + fails + " failed");
        if (fails > 0) System.exit(1);
    }

    static void numbers() {
        eq(Rules.normalizeNumber("+989121234567"), "9121234567");
        eq(Rules.normalizeNumber("00989121234567"), "9121234567");
        eq(Rules.normalizeNumber("09121234567"), "9121234567");
        eq(Rules.normalizeNumber("989121234567"), "9121234567");
        eq(Rules.normalizeNumber("+98 912 123 4567"), "9121234567");
        eq(Rules.normalizeNumber("۰۹۱۲۱۲۳۴۵۶۷"), "9121234567");
        eq(Rules.normalizeNumber("+9830001234"), "30001234");
        eq(Rules.normalizeNumber("98300012345"), "300012345");
        eq(Rules.normalizeNumber("3000505"), "3000505");
        eq(Rules.normalizeNumber("50004001234"), "50004001234");
        eq(Rules.normalizeNumber("+9810004455"), "10004455");
        eq(Rules.normalizeNumber("Snapp"), "");
        eq(Rules.normalizeNumber("BankMellat"), "");
        eq(Rules.normalizeNumber(null), "");
        is(Rules.isAdLine("30001234"), "3000 line");
        is(Rules.isAdLine("100044"), "1000 line");
        is(Rules.isAdLine("50004001234"), "5000 line");
        is(!Rules.isAdLine("9121234567"), "mobile is not an ad line");
        is(!Rules.isAdLine("2188776655"), "Tehran landline is not an ad line");
    }

    static void ads() {
        remove("+989351112222", "حراج ویژه یلدا تا ۷۰٪ تخفیف\nلغو۱۱", Rules.Reason.UNSUBSCRIBE_CODE);
        remove("+989351112222", "تخفیف ویژه لغو 11", Rules.Reason.UNSUBSCRIBE_CODE);
        remove("+989351112222", "جشنواره ... لغو=11", Rules.Reason.UNSUBSCRIBE_CODE);
        remove("+989351112222", "جشنواره ... لغو:۱۱", Rules.Reason.UNSUBSCRIBE_CODE);
        remove("+989351112222", "جشنواره ... لغو ارسال 11", Rules.Reason.UNSUBSCRIBE_CODE);
        remove("+989351112222", "جشنواره (لغو11)", Rules.Reason.UNSUBSCRIBE_CODE);
        remove("+989351112222", "جشنواره\nانصراف ۱۱", Rules.Reason.UNSUBSCRIBE_CODE);
        remove("+989351112222", "جشنواره لغو عدد 11", Rules.Reason.UNSUBSCRIBE_CODE);
        // Arabic letters and a zero-width joiner in the word
        remove("+989351112222", "تخفيف‌ها لغو11", Rules.Reason.UNSUBSCRIBE_CODE);
        // advertising lines, with or without the country code
        remove("30001234", "Big sale today only", Rules.Reason.AD_LINE);
        remove("+9830001234", "فروش ویژه", Rules.Reason.AD_LINE);
        remove("10004455667", "بیمه بدنه با تخفیف", Rules.Reason.AD_LINE);
        remove("2000123", "پیشنهاد ویژه", Rules.Reason.AD_LINE);
        remove("50002060", "قرعه کشی", Rules.Reason.AD_LINE);
        // an unsubscribe code wins over words that look like a bank message
        remove("30001234", "فقط ۳ روز مانده! لغو۱۱", Rules.Reason.UNSUBSCRIBE_CODE);
        remove("30001234", "کد تخفیف شما: OFF50", Rules.Reason.AD_LINE);
    }

    static void keeps() {
        keep("10004455", "کد تایید شما: 48213", Rules.Reason.CODE_OR_BANK);
        keep("10004455", "کد تأیید ورود به سامانه 48213", Rules.Reason.CODE_OR_BANK);
        keep("30001234", "رمز پویا: 552211\nمبلغ 1,000,000", Rules.Reason.CODE_OR_BANK);
        keep("30001234", "رمز یکبار مصرف شما 1234", Rules.Reason.CODE_OR_BANK);
        keep("30001234", "Your verification code is 12345", Rules.Reason.CODE_OR_BANK);
        keep("30001234", "Your OTP: 1234", Rules.Reason.CODE_OR_BANK);
        keep("5000123", "بانک ملت\nبرداشت:1,000,000\nحساب:*1234\nمانده:5,000,000", Rules.Reason.CODE_OR_BANK);
        keep("9999", "واریز: 2,000,000 ریال", Rules.Reason.CODE_OR_BANK);
        keep("BankMellat", "Mande: 12000", Rules.Reason.CODE_OR_BANK);
        // people and normal senders
        keep("+989121234567", "سلام، ساعت ۵ می‌بینمت", Rules.Reason.NONE);
        keep("+989121234567", "قرار رو لغو کن", Rules.Reason.NONE);           // "لغو" without a code
        keep("Snapp", "سفر شما لغو شد", Rules.Reason.NONE);
        keep("+982188776655", "نوبت شما فردا ساعت ۱۰", Rules.Reason.NONE);
        keep("+989121234567", "لغو ۲۰۲۴۱۲ سفارش", Rules.Reason.NONE);          // 6 digits: an order number, not a code
        // a contact is never removed, even with an ad text
        Rules.Verdict v = DEFAULT.check("30001234", "تخفیف لغو11", true);
        is(!v.remove && v.reason == Rules.Reason.CONTACT, "contact kept: " + v);
    }

    static void userLists() {
        Rules r = new Rules(list("30001234", "+989121112233"), list("0935", "+98901"), list("اسنپ"), list("وام", "قرعه کشی"), true);
        check(r, "30001234", "فروش ویژه لغو11", false, Rules.Reason.ALLOWED_SENDER);
        check(r, "09121112233", "تخفیف لغو11", false, Rules.Reason.ALLOWED_SENDER);
        check(r, "+989351234567", "hello", true, Rules.Reason.BLOCKED_SENDER);
        check(r, "09011234567", "hello", true, Rules.Reason.BLOCKED_SENDER);
        check(r, "+989121234567", "وام فوری بدون ضامن", true, Rules.Reason.BLOCKED_WORD);
        check(r, "+989121234567", "قرعه‌کشی بزرگ", true, Rules.Reason.BLOCKED_WORD);
        check(r, "30009999", "کد تخفیف اسنپ لغو11", false, Rules.Reason.ALLOWED_WORD);
        // a blocked word never removes a login code
        check(r, "+989121234567", "کد تایید وام: 1234", false, Rules.Reason.CODE_OR_BANK);
        Rules n = new Rules(list("Snapp"), list("BimehAd"), NONE, NONE, true);
        check(n, "snapp", "تخفیف لغو11", false, Rules.Reason.ALLOWED_SENDER);
        check(n, "BIMEHAD", "hello", true, Rules.Reason.BLOCKED_SENDER);
        check(n, "Digikala", "hello", false, Rules.Reason.NONE);
        // a blocked number does remove even a code-looking text (the user asked for it)
        check(r, "+989351234567", "کد تایید 1234", true, Rules.Reason.BLOCKED_SENDER);
    }

    static void adLinesOff() {
        Rules r = new Rules(NONE, NONE, NONE, NONE, false);
        check(r, "30001234", "Big sale today only", false, Rules.Reason.NONE);
        check(r, "30001234", "Big sale لغو11", true, Rules.Reason.UNSUBSCRIBE_CODE);
    }

    // ------------------------------------------------------------------

    private static List<String> list(String... s) {
        return Arrays.asList(s);
    }

    private static void remove(String from, String body, Rules.Reason why) {
        check(DEFAULT, from, body, true, why);
    }

    private static void keep(String from, String body, Rules.Reason why) {
        check(DEFAULT, from, body, false, why);
    }

    private static void check(Rules r, String from, String body, boolean remove, Rules.Reason why) {
        Rules.Verdict v = r.check(from, body, false);
        is(v.remove == remove && v.reason == why, from + " | " + body.replace('\n', ' ') + " → " + v + " (expected " + (remove ? "REMOVE " : "KEEP ") + why + ")");
    }

    private static void eq(String got, String want) {
        is(want.equals(got), "'" + got + "' expected '" + want + "'");
    }

    private static void is(boolean ok, String what) {
        if (ok) {
            passes++;
        } else {
            fails++;
            System.out.println("FAIL: " + what);
        }
    }
}
