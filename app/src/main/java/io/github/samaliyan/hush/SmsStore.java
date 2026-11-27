package io.github.samaliyan.hush;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.ContactsContract;
import android.provider.Telephony;

import java.util.ArrayList;
import java.util.List;

/**
 * Access to the phone's message store (the same store Google Messages shows).
 *
 * <p>Reading needs the normal SMS permission. Removing needs the hidden "write SMS" switch, which Android
 * gives only to the default messaging app — unless it is set once from a computer:
 * {@code adb shell appops set io.github.samaliyan.hush WRITE_SMS allow}. Without it Android ignores a
 * delete without an error (it returns 0), so the switch is checked first and the result of every delete is
 * checked too.
 */
final class SmsStore {
    static final String OP_WRITE_SMS = "android:write_sms";
    private static final Uri INBOX = Telephony.Sms.Inbox.CONTENT_URI;

    static final class Msg {
        long id;
        String sender, body;
        long date;
    }

    private final Context ctx;

    SmsStore(Context c) {
        ctx = c.getApplicationContext();
    }

    static String adbCommand(Context c) {
        return "adb shell appops set " + c.getPackageName() + " WRITE_SMS allow";
    }

    boolean canRead() {
        return granted(Manifest.permission.READ_SMS) && granted(Manifest.permission.RECEIVE_SMS);
    }

    boolean canReadContacts() {
        return granted(Manifest.permission.READ_CONTACTS);
    }

    /** The "write SMS" switch is on for this app (set from a computer, see the class comment). */
    @SuppressWarnings("deprecation")
    boolean canWrite() {
        try {
            AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            int mode = Build.VERSION.SDK_INT >= 29
                    ? ops.unsafeCheckOpNoThrow(OP_WRITE_SMS, Process.myUid(), ctx.getPackageName())
                    : ops.checkOpNoThrow(OP_WRITE_SMS, Process.myUid(), ctx.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    /** Package of the default messaging app (Google Messages, Samsung Messages …), or null. */
    String defaultSmsApp() {
        try {
            return Telephony.Sms.getDefaultSmsPackage(ctx);
        } catch (Exception e) {
            return null;
        }
    }

    String defaultSmsAppName() {
        String pkg = defaultSmsApp();
        if (pkg == null) return "your messaging app";
        try {
            PackageManager pm = ctx.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return "your messaging app";
        }
    }

    /** Received messages newer than {@code since} (ms), newest first, at most {@code limit}. */
    List<Msg> inbox(long since, int limit) {
        List<Msg> out = new ArrayList<>();
        if (!canRead()) return out;
        String[] cols = {Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE};
        try (Cursor c = ctx.getContentResolver().query(INBOX, cols, Telephony.Sms.DATE + " >= ?",
                new String[]{Long.toString(since)}, Telephony.Sms.DATE + " DESC")) {
            if (c == null) return out;
            while (c.moveToNext() && out.size() < limit) {
                Msg m = new Msg();
                m.id = c.getLong(0);
                m.sender = c.getString(1);
                m.body = c.getString(2);
                m.date = c.getLong(3);
                out.add(m);
            }
        } catch (Exception e) {
            // provider not available (no SIM support, permission revoked meanwhile)
        }
        return out;
    }

    /** @return true when the message is gone from the store. */
    boolean delete(long id) {
        try {
            return ctx.getContentResolver().delete(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, Long.toString(id)), null, null) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** The message is still in the store (as far as Hush can see). */
    boolean exists(long id) {
        try (Cursor c = ctx.getContentResolver().query(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, Long.toString(id)),
                new String[]{Telephony.Sms._ID}, null, null, null)) {
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    /** Puts a removed message back into the inbox (as read). */
    boolean restore(String sender, String body, long date) {
        try {
            ContentValues v = new ContentValues();
            v.put(Telephony.Sms.ADDRESS, sender);
            v.put(Telephony.Sms.BODY, body);
            v.put(Telephony.Sms.DATE, date);
            v.put(Telephony.Sms.DATE_SENT, date);
            v.put(Telephony.Sms.READ, 1);
            v.put(Telephony.Sms.SEEN, 1);
            v.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX);
            return ctx.getContentResolver().insert(INBOX, v) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** The sender is a saved contact (false when the contacts permission was not given). */
    boolean isContact(String sender) {
        if (sender == null || Rules.normalizeNumber(sender).isEmpty() || !canReadContacts()) return false;
        Uri u = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sender));
        try (Cursor c = ctx.getContentResolver().query(u, new String[]{ContactsContract.PhoneLookup._ID}, null, null, null)) {
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean granted(String perm) {
        return ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }
}
