package io.github.samaliyan.hush;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/**
 * Every incoming text: Android sends it to all apps with the SMS permission, at the same time as to the
 * messaging app. Hush checks it at once; if it is advertising, it waits until the messaging app has stored
 * it (a fraction of a second up to a few seconds) and removes it. Store changes are also watched by
 * {@link SweepJob}, so a message stored later is still removed.
 */
public final class SmsReceiver extends BroadcastReceiver {
    private static final long WAIT_MS = 8000;

    @Override public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        final Context c = context.getApplicationContext();
        SmsMessage[] parts;
        try {
            parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        } catch (Exception e) {
            return;
        }
        if (parts == null || parts.length == 0 || parts[0] == null) return;
        final String sender = parts[0].getDisplayOriginatingAddress();
        StringBuilder b = new StringBuilder();
        for (SmsMessage p : parts) if (p != null && p.getDisplayMessageBody() != null) b.append(p.getDisplayMessageBody());
        final String body = b.toString();

        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                handle(c, sender, body);
            } catch (Exception ignored) {
                // never crash on a message
            } finally {
                SweepJob.schedule(c);
                pending.finish();
            }
        }, "hush-sms").start();
    }

    private static void handle(Context c, String sender, String body) throws InterruptedException {
        Prefs prefs = new Prefs(c);
        boolean ad = prefs.enabled() && prefs.rules().check(sender, body, new SmsStore(c).isContact(sender)).remove;
        if (!ad) {
            if (prefs.soundForReal()) Alert.play(c);
            return;
        }
        // Its notification is taken away as soon as the messaging app shows it.
        Cleaner.markRemoved(body);
        NotifListener.cancelMatching();
        String want = Rules.normalizeText(body);
        long since = System.currentTimeMillis() - 5 * 60_000L;     // the store dates a text by its arrival (clock drift allowed)
        long end = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < end) {
            Cleaner.Result r = Cleaner.autoPass(c, since);
            if (contains(r.bodies, want) || contains(r.deniedBodies, want)) return;
            Thread.sleep(400);
        }
    }

    private static boolean contains(java.util.List<String> bodies, String want) {
        for (String s : bodies) if (Rules.normalizeText(s).equals(want)) return true;
        return false;
    }
}
